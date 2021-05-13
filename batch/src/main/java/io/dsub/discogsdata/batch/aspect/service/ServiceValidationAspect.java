package io.dsub.discogsdata.batch.aspect.service;

import io.dsub.discogsdata.common.exception.InvalidArgumentException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
public class ServiceValidationAspect extends AbstractServiceAspect {

  private static final String BLANK_STRING_MESSAGE = "found blank string argument";
  private static final String NULL_STRING_MESSAGE = "found null argument";

  @Around("services()")
  public Object throwOnNullOrBlankStringArgument(ProceedingJoinPoint pjp) throws Throwable {
    Object[] args = pjp.getArgs();
    for (Object arg : args) {
      if (arg == null) {
        throw new InvalidArgumentException(NULL_STRING_MESSAGE);
      }
      if (arg instanceof String) {
        String stringArg = (String) arg;
        if (stringArg.isBlank()) {
          throw new InvalidArgumentException(BLANK_STRING_MESSAGE);
        }
      }
    }
    return pjp.proceed();
  }
}