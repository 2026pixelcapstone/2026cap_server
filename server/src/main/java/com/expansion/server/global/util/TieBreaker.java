package com.expansion.server.global.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러의 Pageable 파라미터에 붙여, 정렬 끝에 조회 대상 엔티티의 id 를 자동으로 붙인다.
 * 예: {@code @TieBreaker(GalleryPost.class) @PageableDefault(...) Pageable pageable}
 *
 * <p>Pageable 을 받는 모든 컨트롤러 파라미터는 이 표시가 있어야 한다. 없으면 서버가 기동하지 않는다
 * ({@code TieBreakerPageableResolver.verify}). 정렬이 없는 목록도 표시만 붙이면 그대로 통과한다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface TieBreaker {

    /** 이 목록이 조회하는 엔티티(쿼리의 기준 엔티티) */
    Class<?> value();
}
