"""Current execution decisions, paired English/Korean document figures.

Build with the svg-diagrams skill's scripts/docfigures/build.py from the repo.
No component performs model/AI work; icons would not distinguish components.
Each relation below is a directed dependency, ordered computation or decision.
"""
from common import BODY, STROKE, canvas, card, cards_row, figure, save


def arrow(c, a, b):
    c.ortho(a[0]+a[2], a[1]+a[3]/2, b[0], b[1]+b[3]/2,
            exit='R', entry='L', color=c.blue, marker='blue', sw=STROKE)


@figure(plain=True)
def pipeline(name, data):
    # Claim: the result depends on the stages in this order, not in parallel.
    c = canvas(1000, 400)
    nodes = cards_row(c, [24, 361, 698], 24, 278, data, c.blue)
    arrow(c, nodes[0], nodes[1]); arrow(c, nodes[1], nodes[2])
    save(c, name)


@figure(plain=True)
def branches(name, source, top, bottom):
    # Claim: the execution condition selects one of two alternative strategies.
    c = canvas(1000, 400)
    a = card(c, 24, 100, 300, c.blue, source[0], [source[1]])
    b = card(c, 490, 24, 486, c.blue, top[0], [top[1]])
    d = card(c, 490, 210, 486, c.blue, bottom[0], [bottom[1]])
    c.ortho(a[0]+a[2], a[1]+a[3]*0.25, b[0], b[1]+b[3]/2,
            exit='R', entry='L', color=c.blue, marker='blue', sw=STROKE)
    c.ortho(a[0]+a[2], a[1]+a[3]*0.75, d[0], d[1]+d[3]/2,
            exit='R', entry='L', color=c.blue, marker='blue', sw=STROKE)
    save(c, name)


for language in ('en', 'ko'):
    ko = language == 'ko'
    # Selection is separate from hydration; count is conditional, described in prose.
    definitions = {
        'two-phase-query-flow': [
            ('ID 선택' if ko else 'Select IDs', ['조건 · 정렬 · 페이지' if ko else 'Filter · sort · page']),
            ('엔티티 조회' if ko else 'Load entities', ['ID 묶음별 조회' if ko else 'Batched ID lookup']),
            ('결과 순서 복원' if ko else 'Restore order', ['1단계 ID 순서' if ko else 'Phase-one ID order'])],
        'search-request-flow': [
            ('입력 변환' if ko else 'Parse input', ['쿼리 파라미터 또는 JSON' if ko else 'Query parameters or JSON']),
            ('DTO 검증' if ko else 'Validate DTO', ['필드 · 연산자 · 값 · 제한' if ko else 'Fields · values · limits']),
            ('JPA 실행' if ko else 'Execute JPA', ['조회 전략 선택' if ko else 'Selected query strategy'])],
        'openapi-customizer-wiring': [
            ('DTO 어노테이션' if ko else 'DTO annotations', ['SearchableField']),
            ('작업 문서 생성' if ko else 'Operation customizer', ['SearchableParams']),
            ('OpenAPI 스키마' if ko else 'OpenAPI schema', ['파라미터 · 예제' if ko else 'Parameters · examples'])],
        'condition-group-evaluation': [
            ('첫 조건' if ko else 'First condition', ['A']),
            ('OR 결합' if ko else 'Combine OR', ['(A OR B)']),
            ('AND 결합' if ko else 'Combine AND', ['((A OR B) AND C)'])]
    }
    for name, data in definitions.items():
        pipeline(f'{language}/_images/{name}', data)
    branches(f'{language}/_images/join-strategy-decision',
        ('키·조인 검사' if ko else 'Inspect key and joins', '검색 실행 계획' if ko else 'Search execution plan'),
        ('단일 키 · 컬렉션 조인 없음' if ko else 'Single key · no collection join', '엔티티 직접 조회' if ko else 'Direct entity query'),
        ('복합 키 또는 컬렉션 조인' if ko else 'Composite key or collection join', 'ID 선택 후 엔티티 조회' if ko else 'ID selection then entity loading'))
    branches(f'{language}/_images/auto-configuration-wiring',
        ('Starter 자동 설정' if ko else 'Starter configuration', '애플리케이션 시작' if ko else 'Application startup'),
        ('Hibernate 기본값' if ko else 'Hibernate defaults', '사용자 설정 우선' if ko else 'Application settings take priority'),
        ('검색 지원 빈' if ko else 'Search support beans', '시간 집계 · 선택적 OpenAPI' if ko else 'Time counts · optional OpenAPI'))
