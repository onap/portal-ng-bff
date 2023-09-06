package org.onap.portalng.bff.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

  @Component
  public static class WebClientBeanPostProcessor implements BeanPostProcessor {

    private final ExchangeStrategies exchangeStrategies;
    private final ExchangeFilterFunction idTokenExchangeFilterFunction;
    private final ExchangeFilterFunction errorHandlingExchangeFilterFunction;
    private final ExchangeFilterFunction logResponseExchangeFilterFunction;

    public WebClientBeanPostProcessor(
        ExchangeStrategies exchangeStrategies,
        @Qualifier(BeansConfig.ID_TOKEN_EXCHANGE_FILTER_FUNCTION)
            ExchangeFilterFunction idTokenExchangeFilterFunction,
        @Qualifier(BeansConfig.ERROR_HANDLING_EXCHANGE_FILTER_FUNCTION)
            ExchangeFilterFunction errorHandlingExchangeFilterFunction,
        @Qualifier(BeansConfig.LOG_RESPONSE_EXCHANGE_FILTER_FUNCTION)
            ExchangeFilterFunction logResponseExchangeFilterFunction) {
      this.exchangeStrategies = exchangeStrategies;
      this.idTokenExchangeFilterFunction = idTokenExchangeFilterFunction;
      this.errorHandlingExchangeFilterFunction = errorHandlingExchangeFilterFunction;
      this.logResponseExchangeFilterFunction = logResponseExchangeFilterFunction;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName)
        throws BeansException {
      return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName)
        throws BeansException {
      if (bean instanceof WebClient.Builder) {
        WebClient.Builder builder = (WebClient.Builder) bean;
        builder
            .exchangeStrategies(exchangeStrategies)
            .filter(idTokenExchangeFilterFunction)
            .filter(errorHandlingExchangeFilterFunction)
            .filter(logResponseExchangeFilterFunction);
      }
      return bean;
    }
  }
}
