package study.querydsl.entity;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.QueryResults;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.dto.MemberDto;
import study.querydsl.dto.QMemberDto;
import study.querydsl.dto.UserDto;

import javax.persistence.EntityManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static study.querydsl.entity.QMember.*;
import static study.querydsl.entity.QTeam.*;

@SpringBootTest
@Transactional
class MemberTest {

    @Autowired
    EntityManager em;


    JPAQueryFactory queryFactory;

    @BeforeEach
    public void before() {
        queryFactory = new JPAQueryFactory(em);

        Team teamA = new Team("teamA");
        Team teamB = new Team("teamB");
        em.persist(teamA);
        em.persist(teamB);

        Member member1 = new Member("member1", 10, teamA);
        Member member2 = new Member("member2", 20, teamA);

        Member member3 = new Member("member3", 30, teamB);
        Member member4 = new Member("member4", 40, teamB);

        em.persist(member1);
        em.persist(member2);
        em.persist(member3);
        em.persist(member4);
    }

    @Test
    @DisplayName("JPQL")
    public void startJPQL() {
        String qlString =
                "select m " +
                "from Member m " +
                "where m.username = :username";

        Member findMember = em.createQuery(qlString, Member.class)
                .setParameter("username", "member1").getSingleResult();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    @DisplayName("Querydsl")
    public void startQuerydsl() {
        Member findMember = queryFactory
                .select(member)
                .from(member)
                .where(member.username.eq("member1")) // //파라미터 바인딩 처리
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void search() {
        queryFactory
                .select(member)
                .from(member)
                .where(
                        member.username.eq("member1"),
                        member.age.eq(10)
                )
                .fetchOne();
    }

    @Test
    public void resultFetch() {
        QueryResults<Member> results = queryFactory
                .selectFrom(member)
                .fetchResults();

        results.getTotal();
        List<Member> content = results.getResults();
    }

    @Test
    public void sort() {
        em.persist(new Member(null, 100));
        em.persist(new Member("member5", 100));
        em.persist(new Member("member6", 100));

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.eq(100))
                .orderBy(member.age.desc(), member.username.asc().nullsLast())
                .fetch();
    }

    @Test
    public void paging1() {
        List<Member> result = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1) // "앞에 몇개를 스킵할거야?" 정하는 것이다 (0부터 시작)
                .limit(2) // 2개를 가져온다는 것
                .fetch();
    }

    @Test
    public void aggregation() {
        List<Tuple> result = queryFactory
                .select(
                        member.count(),   // member가 몇명이야
                        member.age.sum(), // 모든 회원들의 나이 더하기
                        member.age.avg(), // 모든 회원들의 나이 평균
                        member.age.max(), // 최대
                        member.age.min()  // 최소
                )
                .from(member)
                .fetch();

        // Tuple타입을 꺼내기 위해선?
        Tuple tuple = result.get(0);
        assertThat(tuple.get(member.count())).isEqualTo(4);
        assertThat(tuple.get(member.age.sum())).isEqualTo(100);
        assertThat(tuple.get(member.age.avg())).isEqualTo(25);
    }


    @Test
    public void group() throws Exception {
        List<Tuple> result = queryFactory
                .select(team.name, member.age.avg())
                .from(member)
                .join(member.team, team)
                .groupBy(team.name)
                .fetch();

        Tuple teamA = result.get(0);
        Tuple teamB = result.get(1);

        assertThat(teamA.get(team.name)).isEqualTo("teamA");
        assertThat(teamA.get(member.age.avg())).isEqualTo(15); // (10 + 20) / 2
    }

    @Test
    public void join() {
        List<Member> result = queryFactory
                .selectFrom(member)
                .join(member.team, team)
                .where(team.name.eq("teamA"))
                .fetch();

        assertThat(result)
                .extracting("username")
                .containsExactly("member1", "member2");
    }

    @Test
    public void theta_join() {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));

        List<Member> result = queryFactory
                .select(member)
                .from(member, team)
                .where(member.username.eq(team.name))
                .fetch();
    }

    @Test
    public void join_on_relation() {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));
        em.persist(new Member("teamC"));

        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member)
                .leftJoin(team).on(member.username.eq(team.name))
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    @Test
    public void fetchJoinUse() throws Exception {
        em.flush();
        em.clear();
        Member findMember = queryFactory
                .selectFrom(member)
                .join(member.team, team).fetchJoin()
                .where(member.username.eq("member1"))
                .fetchOne();
    }

    /**
     * 나이가 가장 많은 회원 조회
     */
    @Test
    public void subQuery() {
        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.in(
                        JPAExpressions
                                .select(memberSub.age)
                                .from(memberSub)
                                .where(memberSub.age.gt(10)) // 10살 이상인 사람들 -> 20살, 40살 등등
                ))
                .fetch();
    }

    @Test
    public void tupleProjection() {
        List<Tuple> result = queryFactory
                .select(member.username, member.age)
                .from(member)
                .fetch();

        for (Tuple tuple : result) {
            String username = tuple.get(member.username);
            Integer age = tuple.get(member.age);
            System.out.println("username=" + username);
            System.out.println("age=" + age);
        }
    }

    @Test
    public void findDtoByJPQL() {
        List<MemberDto> result = queryFactory
                .select(new QMemberDto(member.username, member.age))
                .from(member)
                .fetch();
    }

    /**
     * 유저 이름이 member1이고,
     * 나이가 10살인 사람을 찾고 싶은 경우이다.
     */
    @Test
    public void 동적쿼리_BooleanBuilder() throws Exception {
        String usernameParam = "member1";
        Integer ageParam = 10;

        List<Member> result = searchMember(usernameParam, ageParam);
        Assertions.assertThat(result.size()).isEqualTo(1);
    }

    // 여기로 들어오는 파라미터가 null인가, 아닌가에 따라서 달라지는 것이다.
    private List<Member> searchMember(String usernameCond, Integer ageCond) {
        BooleanBuilder builder = new BooleanBuilder();

        if (usernameCond != null) {
            builder.and(member.username.eq(usernameCond)); //builder는 and, or로 조립 할수 있다는 장점이 있다
            // 풀이 하자면, usernameCond가 null이 아니면 builder에 and() 조건을 넣어주는 것이된다.
        }
        if (ageCond != null) {
            builder.and(member.age.eq(ageCond));
        }
        return queryFactory
                .selectFrom(member)
                .where(builder) // 여기가 포인트인 것이다
                .fetch();
    }


    @Test
    public void 동적쿼리_WhereParam() throws Exception {
        String usernameParam = "member1";
        Integer ageParam = 10;

        List<Member> result = searchMember2(usernameParam, ageParam);
        Assertions.assertThat(result.size()).isEqualTo(1);
    }

    private List<Member> searchMember2(String usernameCond, Integer ageCond) {
        return queryFactory
                .selectFrom(member)
                .where(usernameEq(usernameCond), ageEq(ageCond))
                .fetch();
    }

    private BooleanExpression usernameEq(String usernameCond) {
        if (usernameCond == null) {
            return null;
        }
        return member.username.eq(usernameCond);
    }

    private BooleanExpression ageEq(Integer ageCond) {
        if (ageCond == null) {
            return null;
        }
        return member.age.eq(ageCond);
    }


}