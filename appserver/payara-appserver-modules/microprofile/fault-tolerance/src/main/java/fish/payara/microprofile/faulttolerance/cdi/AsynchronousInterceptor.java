package fish.payara.microprofile.faulttolerance.cdi;

import org.eclipse.microprofile.faulttolerance.Asynchronous;

import javax.annotation.Priority;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;

@Interceptor
@Asynchronous
@Priority(Interceptor.Priority.PLATFORM_AFTER + 16)
public class AsynchronousInterceptor extends FaultToleranceInterceptor {

    @AroundInvoke
    public Object intercept(InvocationContext invocationContext) throws Exception {
        return shouldIntercept(invocationContext) ? super.intercept(invocationContext) : invocationContext.proceed();
    }

}
