package fish.payara.microprofile.faulttolerance.cdi;

import org.eclipse.microprofile.faulttolerance.CircuitBreaker;

import javax.annotation.Priority;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;

@Interceptor
@CircuitBreaker
@Priority(Interceptor.Priority.PLATFORM_AFTER + 16)
public class CircuitBreakerInterceptor extends FaultToleranceInterceptor {

    @AroundInvoke
    public Object intercept(InvocationContext invocationContext) throws Exception {
        return shouldIntercept(invocationContext) ? super.intercept(invocationContext) : invocationContext.proceed();
    }

}
