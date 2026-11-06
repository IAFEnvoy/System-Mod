package com.iafenvoy.system.screen;

import com.iafenvoy.saturn.screen.hud.HudEditScreen;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.config.file.ClientConfig;
import com.iafenvoy.system.data.card.CardContext;
import com.iafenvoy.system.data.card.CardUpdate;
import com.iafenvoy.system.data.card.DisplayCard;
import com.iafenvoy.system.data.card.DisplayCardKey;
import com.iafenvoy.system.module.client.ClientModuleCaches;
import com.iafenvoy.system.module.client.DisplayCardCache;
import com.iafenvoy.system.registry.SystemHudEntries;
import com.iafenvoy.system.screen.card.CardPresentation;
import com.iafenvoy.system.screen.card.CardTemplates;
import com.iafenvoy.system.screen.gui.AssistantItems;
import com.iafenvoy.system.screen.gui.AssistantView;
import com.sighs.apricityui.client.gui.ApricityGuiLayers;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.render.GeometryQueryScope;
import com.sighs.apricityui.style.Transition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.*;

/**
 * Passive persistent cards use their own document, with no notification queue, speech session or countdown.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class AssistantCardDisplay {
    private static Document document;
    private static long generation = -1;
    private static long cardsRevision = -1;
    private static long templatesRevision = -1;
    private static long failedRevision = -1;
    private static Set<DisplayCardKey> activeCards = Set.of();
    private static final Map<DisplayCardKey, RenderedCard> RENDERED = new LinkedHashMap<>();

    private enum Phase {
        WAITING, SHOWN, EXITING
    }

    private static final class RenderedCard {
        private DisplayCard snapshot;
        private final Element element;
        private Phase phase = Phase.WAITING;

        private RenderedCard(DisplayCard snapshot, Element element) {
            this.snapshot = snapshot;
            this.element = element;
        }
    }

    private AssistantCardDisplay() {
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        DisplayCardCache cache = ClientModuleCaches.DISPLAY_CARDS;
        cache.tick();
        Minecraft minecraft = Minecraft.getInstance();
        if (!canRender()) {
            unmount();
            return;
        }
        boolean shown = canShow(minecraft.screen) && cache.cards().stream().anyMatch(card -> card.data().isPresent());
        if (!shown && document == null) return;
        if (document == null || document.isDisposed()) {
            unmount();
            if (!shown) return;
            if (failedRevision == cache.revision() + CardTemplates.revision()) return;
            try {
                AssistantPages.prepare(DisplayCardsHudEntry.TEMPLATE);
                document = Document.create(DisplayCardsHudEntry.TEMPLATE);
                if (document == null) throw new IllegalStateException("Missing persistent card HUD template");
                document.setReloadPersistent(true);
            } catch (RuntimeException error) {
                failedRevision = cache.revision() + CardTemplates.revision();
                SystemMod.LOGGER.warn("Unable to create persistent card HUD", error);
                unmount();
                return;
            }
        }
        if (generation != document.getRefreshGeneration()) {
            if (document.getElementById("card_display") == null) {
                failedRevision = cache.revision() + CardTemplates.revision();
                SystemMod.LOGGER.warn("Persistent card HUD template is missing card_display");
                unmount();
                return;
            }
            generation = document.getRefreshGeneration();
            cardsRevision = templatesRevision = -1;
            activeCards = Set.of();
            RENDERED.clear();
            SystemHudEntries.DISPLAY_CARDS.detach();
            AssistantThemes.apply(document);
            bindMouse();
        }
        if (shown && (cardsRevision != cache.revision() || templatesRevision != CardTemplates.revision())) {
            boolean reload = templatesRevision != CardTemplates.revision();
            cardsRevision = cache.revision();
            templatesRevision = CardTemplates.revision();
            try (Document.ContextScope ignored = Document.withContext(document)) {
                Element root = document.getElementById("card_display");
                double scroll = root.scrollTop;
                Set<DisplayCardKey> next = new HashSet<>();
                for (DisplayCard card : cache.cards())
                    card.data().ifPresent(data -> {
                        try {
                            RenderedCard previous = RENDERED.get(card.key());
                            if (reload || previous == null || !previous.snapshot.equals(card)) {
                                Element content = Element.init(CardPresentation.of(CardUpdate.show(data), "", CardContext.Mode.HUD, 0).render(document));
                                if (previous == null) {
                                    Element row = Element.init(AssistantView.element(document, "div", "card_display_entry", ""));
                                    row.appendChild(content);
                                    RENDERED.put(card.key(), new RenderedCard(card, row));
                                } else {
                                    // The wrapper owns motion; progress/template changes only replace its content.
                                    AssistantView.reconcile(previous.element, List.of(content));
                                    previous.snapshot = card;
                                }
                            }
                            next.add(card.key());
                        } catch (RuntimeException error) {
                            // Isolate broken custom templates; retry only when the snapshot or resources change.
                            SystemMod.LOGGER.warn("Unable to render persistent card {}", card.key(), error);
                        }
                    });
                // Outgoing cards retain their slot until their exit finishes, including when the last pin is removed.
                AssistantView.reconcile(root, RENDERED.values().stream().map(card -> card.element).toList());
                activeCards = next;
                root.scrollTop = root.targetScrollTop = scroll;
            }
        }
        if (!shown) {
            activeCards = Set.of();
            cardsRevision = -1;
        }
        SystemHudEntries.DISPLAY_CARDS.apply(document);
        interaction(shown && minecraft.screen instanceof ChatScreen);
        advanceAnimation();
        if (RENDERED.isEmpty()) unmount();
    }

    private static boolean canRender() {
        Minecraft minecraft = Minecraft.getInstance();
        // Retain outgoing documents over other screens until their transitions finish.
        return minecraft.player != null && minecraft.level != null && !minecraft.options.hideGui;
    }

    private static boolean canShow(Screen screen) {
        return canRender() && SystemHudEntries.DISPLAY_CARDS.visible()
                && !(screen instanceof HudEditScreen)
                && (screen == null || screen instanceof ChatScreen || ClientConfig.INSTANCE.get().cardsOnOtherScreens());
    }

    private static void advanceAnimation() {
        try (Document.ContextScope ignored = Document.withContext(document);
             GeometryQueryScope geometry = GeometryQueryScope.open()) {
            Element root = document.getElementById("card_display");
            String offset = SystemHudEntries.DISPLAY_CARDS.slideOffset(document) + "px";
            boolean changed = !offset.equals(root.getInlineStylePropertyValue("--card-slide-offset"));
            if (changed) root.setInlineStyleProperty("--card-slide-offset", offset);
            boolean animations = ClientConfig.INSTANCE.get().cardAnimations();
            for (Iterator<Map.Entry<DisplayCardKey, RenderedCard>> iterator = RENDERED.entrySet().iterator(); iterator.hasNext(); ) {
                Map.Entry<DisplayCardKey, RenderedCard> entry = iterator.next();
                RenderedCard card = entry.getValue();
                boolean active = activeCards.contains(entry.getKey());
                if (!active && (!animations || card.phase == Phase.WAITING || card.phase == Phase.EXITING && !Transition.isActive(card.element))) {
                    card.element.remove();
                    iterator.remove();
                    continue;
                }
                // Without motion, show the final style immediately, including when a transition was already running.
                if (active && animations && card.phase == Phase.WAITING && (document.getLayout().needsInitialFullCommit()
                        || card.element.getLayout().getCommittedRectIfValid() == null)) continue;
                Element.DOMTokenList classes = card.element.getClassList();
                if (classes.contains("animated") != animations) {
                    classes.toggle("animated", animations);
                    changed = true;
                }
                if (active && card.phase != Phase.SHOWN) {
                    classes.add("shown");
                    card.phase = Phase.SHOWN;
                    changed = true;
                } else if (!active && card.phase == Phase.SHOWN) {
                    classes.remove("shown");
                    card.phase = Phase.EXITING;
                    changed = true;
                }
            }
            // Publish transition targets once; AUI's renderer alone advances their transform/opacity each frame.
            if (changed) document.commitPendingStyleRecalcForRender();
        }
    }

    private static void interaction(boolean enabled) {
        if (document == null || document.isDisposed()) return;
        Element.DOMTokenList classes = document.body.getClassList();
        if (classes.contains("interactive") != enabled) classes.toggle("interactive", enabled);
        if (document.getFocusedElement() != null) document.clearFocus();
        if (!enabled) document.setPressedElement(null);
    }

    private static void bindMouse() {
        Document mounted = document;
        Element root = mounted.getElementById("card_display");
        for (String type : List.of("mousedown", "mouseup", "wheel"))
            root.addInternalEventListener(type, event -> {
                if (document == mounted && canShow(Minecraft.getInstance().screen)
                        && Minecraft.getInstance().screen instanceof ChatScreen && event instanceof MouseEvent mouse) {
                    mouse.consumeNative();
                    mounted.clearFocus();
                }
            });
    }

    @SubscribeEvent
    public static void opening(ScreenEvent.Opening event) {
        updateVisibility(event.getNewScreen());
    }

    @SubscribeEvent
    public static void closing(ScreenEvent.Closing event) {
        if (event.getScreen() instanceof ChatScreen) interaction(false);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void mouse(InputEvent.MouseButton.Pre event) {
        prepareMouse();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void scroll(ScreenEvent.MouseScrolled.Pre event) {
        prepareMouse();
    }

    private static void prepareMouse() {
        updateVisibility(Minecraft.getInstance().screen);
    }

    private static void updateVisibility(Screen screen) {
        if (!canRender()) {
            unmount();
            return;
        }
        boolean shown = canShow(screen);
        interaction(shown && screen instanceof ChatScreen);
        if (shown || document == null || document.isDisposed()) return;
        // Stop hit testing immediately, but leave the persistent overlay mounted for its exit.
        activeCards = Set.of();
        cardsRevision = -1;
        advanceAnimation();
        if (RENDERED.isEmpty()) unmount();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tooltip(ScreenEvent.Render.Foreground event) {
        if (canShow(event.getScreen()) && event.getScreen() instanceof ChatScreen
                && AssistantItems.tooltip(document, event.getGuiGraphics(), event.getMouseX(), event.getMouseY()))
            ApricityGuiLayers.submitUi(event.getGuiGraphics());
    }

    public static void unmount() {
        if (document != null) document.remove();
        document = null;
        RENDERED.clear();
        activeCards = Set.of();
        generation = cardsRevision = templatesRevision = -1;
        SystemHudEntries.DISPLAY_CARDS.detach();
    }
}
