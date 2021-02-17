package fish.payara.microprofile.faulttolerance.cdi;

import org.eclipse.microprofile.faulttolerance.Bulkhead;

import javax.annotation.Priority;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;

@Interceptor
@Bulkhead
@Priority(Interceptor.Priority.PLATFORM_AFTER + 16)
public class BulkheadInterceptor extends FaultToleranceInterceptor {

    @AroundInvoke
    public Object intercept(InvocationContext invocationContext) throws Exception {
        return shouldIntercept(invocationContext) ? super.intercept(invocationContext) : invocationContext.proceed();
    }

}
