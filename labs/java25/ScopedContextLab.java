import java.util.NoSuchElementException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Java 25, sin preview ni dependencias: alcance y aislamiento de ScopedValue.
 * El contexto sintético solo correlaciona operaciones; no concede permisos ni
 * sustituye SecurityContext, MDC o la propagación de OpenTelemetry.
 */
public final class ScopedContextLab {
    private static final ScopedValue<RequestContext> CONTEXT = ScopedValue.newInstance();

    private record RequestContext(String correlationId) {}

    public static void main(String[] args) throws Exception {
        bindingAndCleanup();
        nestedBindingRestoresOuterValue();
        exceptionCleansBinding();
        virtualThreadsKeepContextsSeparate();
        childThreadDoesNotInherit();
        explicitRebindingAcrossThreads();
        System.out.println("PASS: 6 scoped-context checks (Java 25, no preview)");
    }

    private static void bindingAndCleanup() {
        check(!CONTEXT.isBound(), "initially unbound");
        ScopedValue.where(CONTEXT, new RequestContext("order-1")).run(() ->
                check(currentCorrelation().equals("order-1"), "read through a method call"));
        check(!CONTEXT.isBound(), "normal completion removes binding");
        try {
            CONTEXT.get();
            throw new AssertionError("unbound get must fail");
        } catch (NoSuchElementException expected) {
            // No request context survives outside its bounded operation.
        }
    }

    private static void nestedBindingRestoresOuterValue() {
        ScopedValue.where(CONTEXT, new RequestContext("outer")).run(() -> {
            ScopedValue.where(CONTEXT, new RequestContext("inner")).run(() ->
                    check(currentCorrelation().equals("inner"), "nested value"));
            check(currentCorrelation().equals("outer"), "outer value restored");
        });
        check(!CONTEXT.isBound(), "nested operation leaves no binding");
    }

    private static void exceptionCleansBinding() {
        var failure = new IllegalStateException("synthetic failure");
        try {
            ScopedValue.where(CONTEXT, new RequestContext("failed-order")).run(() -> {
                check(CONTEXT.isBound(), "bound before failure");
                throw failure;
            });
            throw new AssertionError("operation must throw");
        } catch (IllegalStateException actual) {
            check(actual == failure, "original failure preserved");
        }
        check(!CONTEXT.isBound(), "exception removes binding");
    }

    private static void virtualThreadsKeepContextsSeparate() throws Exception {
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> isolatedOperation("order-a", barrier));
            var second = executor.submit(() -> isolatedOperation("order-b", barrier));
            check(first.get(5, TimeUnit.SECONDS).equals("order-a"), "first context isolated");
            check(second.get(5, TimeUnit.SECONDS).equals("order-b"), "second context isolated");
        }
        check(!CONTEXT.isBound(), "worker bindings do not reach caller");
    }

    private static String isolatedOperation(String correlationId, CyclicBarrier barrier) throws Exception {
        check(Thread.currentThread().isVirtual(), "operation runs in a virtual thread");
        var result = ScopedValue.where(CONTEXT, new RequestContext(correlationId)).call(() -> {
            barrier.await(5, TimeUnit.SECONDS); // Both bindings exist before either is inspected.
            check(currentCorrelation().equals(correlationId), "concurrent binding not overwritten");
            return currentCorrelation();
        });
        check(!CONTEXT.isBound(), "worker binding cleaned after operation");
        return result;
    }

    private static void childThreadDoesNotInherit() throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            ScopedValue.where(CONTEXT, new RequestContext("parent")).call(() -> {
                var child = executor.submit(() -> {
                    check(Thread.currentThread().isVirtual(), "child is virtual");
                    return CONTEXT.isBound();
                });
                check(!child.get(5, TimeUnit.SECONDS), "ordinary child does not inherit binding");
                return null;
            });
        }
        check(!CONTEXT.isBound(), "parent binding cleaned");
    }

    private static void explicitRebindingAcrossThreads() throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            ScopedValue.where(CONTEXT, new RequestContext("captured")).call(() -> {
                var captured = CONTEXT.get(); // Select deliberately what crosses the thread boundary.
                var child = executor.submit(() -> {
                    check(!CONTEXT.isBound(), "child starts unbound");
                    var result = ScopedValue.where(CONTEXT, captured).call(ScopedContextLab::currentCorrelation);
                    check(!CONTEXT.isBound(), "explicit child binding cleaned");
                    return result;
                });
                check(child.get(5, TimeUnit.SECONDS).equals("captured"), "explicit propagation");
                check(currentCorrelation().equals("captured"), "parent remains bound");
                return null;
            });
        }
        check(!CONTEXT.isBound(), "explicit propagation leaves caller unbound");
    }

    private static String currentCorrelation() {
        return CONTEXT.get().correlationId();
    }

    private static void check(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError(description);
        }
    }
}
