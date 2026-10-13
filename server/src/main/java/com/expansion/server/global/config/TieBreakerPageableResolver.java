package com.expansion.server.global.config;

import com.expansion.server.global.util.PageableUtils;
import com.expansion.server.global.util.TieBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Pageable 파라미터에 {@link TieBreaker} 가 붙어 있으면, 스프링 데이터가 만든 Pageable 의 정렬 끝에 엔티티 id 를 붙인다.
 *
 * <p>스프링 데이터의 Pageable 해석기보다 먼저 쓰이도록 가장 앞 순서로 등록하고, 실제 해석은 그 해석기에 맡긴다.
 * 기동 시 모든 컨트롤러를 검사해 TieBreaker 없는 Pageable 파라미터가 있으면 기동을 멈춘다(빠뜨림 방지).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TieBreakerPageableResolver implements WebMvcConfigurer, HandlerMethodArgumentResolver {

    private static final Logger log = LoggerFactory.getLogger(TieBreakerPageableResolver.class);

    private final ObjectProvider<PageableHandlerMethodArgumentResolver> pageableResolver;
    /** true(기본) = 표시 누락 시 기동 중단(로컬), false = 에러 로그만 남기고 기동(프로덕션 — 실수 하나로 전체 장애 방지) */
    private final boolean strict;

    public TieBreakerPageableResolver(ObjectProvider<PageableHandlerMethodArgumentResolver> pageableResolver,
                                      @Value("${pagination.tie-breaker-strict:true}") boolean strict) {
        this.pageableResolver = pageableResolver;
        this.strict = strict;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(this);
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return Pageable.class.equals(parameter.getParameterType()) && parameter.hasParameterAnnotation(TieBreaker.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Pageable pageable = pageableResolver.getObject()
                .resolveArgument(parameter, mavContainer, webRequest, binderFactory);
        TieBreaker tieBreaker = parameter.getParameterAnnotation(TieBreaker.class);
        return PageableUtils.withTieBreaker(pageable, tieBreaker.value());
    }

    /** 기동 검사: Pageable 을 받는 컨트롤러 파라미터는 모두 @TieBreaker(엔티티) 가 있어야 한다 */
    @EventListener
    public void verify(ContextRefreshedEvent event) {
        List<String> missing = new ArrayList<>();
        event.getApplicationContext().getBeansOfType(RequestMappingHandlerMapping.class).values()
                .forEach(mapping -> mapping.getHandlerMethods().values().forEach(handler ->
                        Arrays.stream(handler.getMethodParameters())
                                .filter(p -> Pageable.class.equals(p.getParameterType()))
                                .forEach(p -> {
                                    TieBreaker tb = p.getParameterAnnotation(TieBreaker.class);
                                    if (tb == null) {
                                        missing.add(handler.getBeanType().getSimpleName() + "." + handler.getMethod().getName());
                                    } else {
                                        PageableUtils.idProperty(tb.value());   // @Id 없는 클래스면 여기서 실패
                                    }
                                })));
        if (missing.isEmpty()) {
            return;
        }
        String message = "Pageable 파라미터에 @TieBreaker(조회 엔티티.class) 가 없습니다(정렬 끝 id 누락 방지): " + missing;
        if (strict) {
            throw new IllegalStateException(message);
        }
        // 표시가 빠진 목록은 id 없이 동작(이전과 같은 동작) — 로그로 알리고 기동은 계속
        log.error(message);
    }
}
