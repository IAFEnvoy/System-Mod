# 统一 API 管理

凭据集中保存在 `config/system/system-api.json`。一份凭据包含该账号需要的所有鉴权字段，同一份 DeepSeek Key 可以用于模型、搜索、TTS 和 ASR。各用途只选择凭据 ID，由对应的 `CredentialManager` 自动构造实际服务，不再选择额外的服务 ID。

## 设置流程

1. 在系统输入框输入 `/settings`，打开“API Key管理”。顶部只有类型下拉框和“添加”按钮；选择 DeepSeek、OpenAI、百炼或 NLS 等凭据类型后添加账号。
2. 下方每个账号单独一张卡片，显示类型、删除按钮、名称和该类型需要的鉴权字段，可同时编辑多个账号，不用切换下拉框。直接填写的非空值优先；留空时由凭据实现自动读取环境变量，实际环境变量值不显示、不写回文件。
3. 单人 / 局域网房主可在“文字交互”中为模型 / 搜索选择凭据；连接远程服务器时隐藏这些选择和连接字段，文字服务由服务器本机配置。在“语音交互”中为本机识别 / 播报选择凭据。API Key管理只编辑本进程的账号；各用途选择器只列出对应服务 Manager 支持的凭据类型。

修改立即保存并标记脏状态。更换模型凭据类型时应用供应商默认地址、协议和模型；更换搜索凭据类型时应用默认地址；更换 TTS 凭据类型时应用默认模型与音色。同类型换账号保留连接、模型和音色的自定义设置。删除凭据会清空所有引用，包括扩展用途，不会自动改用其他账号。

新建账号允许鉴权字段为空，先创建再逐项填写，不要求通过供应商验证。新增账号追加到列表末尾，编辑已有账号保留顺序；创建异常与文件保存失败分别提示，创建异常日志仅记录异常类型，避免输出凭据内容。

每张卡片分别持有编辑副本，输入时只提交对应账号的快照并更新服务选择器或就绪提示，不重建卡片，保留其他账号的字段、焦点与光标。添加、删除及外部配置重载时才重新构建账号卡片列表。

## 配置结构

默认文件位于 JAR 的 `defaults/system-api.json`，完整示例见 [system-api.json](../examples/agent/system-api.json)。模型、搜索和语音各自的配置只保存连接、音色、地域、预算等选项。

```json
{
  "credentials": [
    {
      "id": "deepseek",
      "name": "DeepSeek",
      "credential": {
        "type": "system:deepseek",
        "api_key": ""
      }
    }
  ],
  "bindings": {
    "model": "deepseek",
    "search": "deepseek",
    "tts": "deepseek",
    "asr": "deepseek"
  }
}
```

| 字段 | 含义 |
| --- | --- |
| `credentials` | 本进程的凭据账号列表 |
| `id` | 唯一账号 ID，修改名称不影响引用 |
| `name` | 设置页显示名称；为空时显示 ID |
| `credential` | 实现 `Credential` 的类型化对象 |
| `credential.type` | `system:credential_type` 注册表中的类型 ID |
| `credential` 内其他字段 | 由该类型自己的 MapCodec 定义 |
| `bindings` | 用途 ID → 凭据账号 ID；内置 `model`、`search`、`tts`、`asr` |

重复账号 ID、失效引用或 Manager 不支持的类型不会自动选择其他账号。凭据字段使用 `lenientOptionalFieldOf` 独立容错，必需字段未填全时由实际服务报告未配置。无认证本地模型仍可在 `system-model.json` 配置 `apiKeyRequired: false`。

旧的 `type` / `fields` 账号结构及“凭据 + 服务”绑定已移除，不做旧格式迁移；已有开发配置请按新示例调整。

## 类型与环境变量

直接填写值优先，否则按表中顺序读取第一个非空环境变量。变量必须由发起请求的进程继承；修改进程环境变量后需重启。

| 凭据类型 | 字段 | 自动识别的环境变量 |
| --- | --- | --- |
| `system:deepseek` | `api_key` | `SYSTEM_DEEPSEEK_API_KEY`、`DEEPSEEK_API_KEY` |
| `system:openai` | `api_key` | `OPENAI_API_KEY` |
| `system:dashscope` | `api_key` | `DASHSCOPE_API_KEY` |
| `system:dashscope` | 可选 `workspace` | `DASHSCOPE_WORKSPACE_ID` |
| `system:aliyun_nls` | `app_key` | `ALIYUN_NLS_APP_KEY` |
| `system:aliyun_nls` | `access_key_id` | `ALIYUN_AK_ID`、`ALIBABA_CLOUD_ACCESS_KEY_ID` |
| `system:aliyun_nls` | `access_key_secret` | `ALIYUN_AK_SECRET`、`ALIBABA_CLOUD_ACCESS_KEY_SECRET` |

同一类型可新建多个账号。空字段使用该类型相同的环境变量；不同账号需要不同值时直接填写。

## 内置服务映射

公开入口为 `registry.SystemServices`。Manager 按 Credential 的精确 Java 类选择构造函数，同一类型在同一 Manager 中只能登记一次。

| Manager | 凭据类型 | 实际服务 |
| --- | --- | --- |
| `MODEL` | `DeepSeekCredential`、`OpenAiCredential`、`DashScopeCredential` | `OpenAiCompatibleClient`，各自提供不同默认连接 |
| `SEARCH` | `DeepSeekCredential` | `DeepSeekSearchClient` |
| `TTS` | `DeepSeekCredential` | `DeepSeekTtsProvider` |
| `TTS` | `DashScopeCredential` | `AliyunTtsProvider` |
| `ASR` | `DeepSeekCredential` | `DeepSeekAsrProvider` |
| `ASR` | `AliyunNlsCredential` | `AliyunAsrProvider` |

默认模型和搜索共用 DeepSeek 账号，TTS / ASR 仍默认使用百炼 / NLS。选择 DeepSeek 凭据即可切换语音服务，见 [DeepSeek 语音](deepseek-speech.md)。配置凭据不会授予玩家联网模块或供应商 API 权限。

## 扩展方式

实现 `Credential`，提供自己的 `MapCodec` 和 `createWidget()`，再在公共模组入口向正式注册表登记：

```java
public static final DeferredRegister<MapCodec<? extends Credential>> CREDENTIALS =
        DeferredRegister.create(SystemResourceKeys.CREDENTIAL_TYPE, "example");
public static final DeferredHolder<MapCodec<? extends Credential>, MapCodec<ExampleCredential>> ACCOUNT =
        CREDENTIALS.register("account", () -> ExampleCredential.CODEC);

// In the common mod constructor:
CREDENTIALS.register(bus);
SystemServices.TTS.register(ExampleCredential.class, ExampleTtsProvider::new);
SystemServices.ASR.register(ExampleCredential.class, credential -> new ExampleAsrProvider(credential, options));
```

服务构造函数接收具体 Credential，自行读取有类型的 getter；无需中转为字段 Map。构造、`configured()`、默认参数和请求构建必须只做本地操作，不签发 Token、不建立网络连接。内置 `HttpTransport` 在首次实际请求时才创建 HttpClient。构造失败交由调用方处理，Manager 不缓存或关闭实例。

设置页从注册表枚举凭据类型，使用 `credential.<namespace>.<path>` 翻译键作为扩展类型名称，无翻译时显示 ID。无参数新建通过该类型 MapCodec 解析空对象，因此字段应提供空值默认。内置名称沿用 `system.api.type.*`。

`createWidget()` 在 `Document.withContext(document)` 中调用；可复用 `CredentialWidgets.create/field`，输入事件更新当前凭据。设置页先复制一份凭据供编辑，再在冒泡的 `input` / `change` 事件后通过 Codec 创建新快照、调用 `ConfigInstance.set()` 和 `save()`。控件应在自身字段监听器中完成修改，不异步修改旧配置对象。密码字段和凭据 `toString()` 应避免泄露密钥。

扩展也可以定义独立的服务接口、Manager 和用途 ID：

```java
CredentialManager<MyService> services = new CredentialManager<>();
services.register(ExampleCredential.class, ExampleService::new);

// Save the account ID for this use; never save a service implementation or resolved secret.
ApiConfig.INSTANCE.set(ApiConfig.INSTANCE.get().bind("example:lookup", accountId));
ApiConfig.INSTANCE.save();
try (MyService service = ApiConfig.INSTANCE.get().create("example:lookup", services).orElseThrow()) {
    // Schedule any network work on the caller's worker executor.
}
```

独立用途由扩展提供自己的选择界面；四种内置用途会自动包含已登记的兼容凭据类型。语音供应商还需实现请求构建、协议、用量与关闭逻辑，按需补充价格表。

## 执行端与生命周期

模型和搜索由服务端读取自己的文件与环境变量。只有本机持有集成服务器的单人玩家 / 局域网房主可以在设置页修改，依据是 `Minecraft.getSingleplayerServer()`，不检查 OP。独立服的凭据、用途绑定和连接参数由服务器本机配置文件或环境变量管理；远程客户端（包括 OP）不提供编辑入口，也没有修改这些配置的网络接口。

远程客户端隐藏模型 / 搜索选择器和连接字段，不读取本机模型配置冒充服务器配置，不构造文字服务或计算其本机凭据状态。已绑定玩家只显示服务端同步的模型就绪状态；未绑定或尚未收到状态时不推断未配置。API Key管理提示当前只配置本机语音凭据。凭据内容、账号列表和连接配置均不在两端传输；服务端保留 AI 权限及就绪状态同步，历史与模式等玩家偏好仍可正常修改。

TTS 和 ASR 只读取客户端本机账号。凭据内容不进入注册表条目、网络包、玩家附件、存档或对话；注册表只登记类型 Codec。直接填写的值保存在本机 JSON 中，环境变量实际值仅在使用时解析。语音用量继续只在本机统计。

模型和搜索每次调用选择实际服务，服务捕获本次连接与鉴权快照，请求完成即关闭；宿主服务关闭时会释放活动请求。语音会话各自持有服务与参数，不受后续账号编辑影响，网络请求和音频合成仍在语音工作线程执行，原有取消、缓存及用量统计保留。

## 音色工具

`tools/voice_design.py --config` 支持统一文件或相邻的 `system-speech.json`。按 `bindings.tts` 查找 `system:dashscope` 类型账号，读取嵌套 `credential` 的 `api_key` / `workspace`，留空时识别对应环境变量，地域读取语音配置。该工具只支持百炼，详见 [音色设计](tts.md)。
