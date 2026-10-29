package com.iafenvoy.saturn.profiling;

// Close on the opening thread, in reverse nesting order, normally with try-with-resources.
public interface ProfilerScope extends AutoCloseable {
    @Override
    void close();
}
