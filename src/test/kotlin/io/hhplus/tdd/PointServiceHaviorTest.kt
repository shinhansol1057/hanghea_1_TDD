package io.hhplus.tdd

import io.hhplus.tdd.database.PointHistoryTable
import io.hhplus.tdd.database.UserPointTable
import io.hhplus.tdd.point.*
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.*

class PointServiceBehaviorTest : BehaviorSpec({

    val userPointTable = mockk<UserPointTable>()
    val pointHistoryTable = mockk<PointHistoryTable>()
    val service = PointService(userPointTable, pointHistoryTable)

    beforeTest {
        clearMocks(userPointTable, pointHistoryTable, recordedCalls = true, answers = false)
    }

    given("신규 사용자") {
        `when`("포인트를 조회하면") {
            beforeTest {
                every { userPointTable.selectById(1L) } returns UserPoint(1, 0, 0)
            }
            then("0 포인트를 반환해야 한다") {
                val userPoint = service.getPoint(1L)
                userPoint.id shouldBe 1L
                userPoint.point shouldBe 0L
                verify(exactly = 1) { userPointTable.selectById(1L) }
                confirmVerified(userPointTable)
            }
        }
    }

    given("충전 로직") {

        `when`("100원을 충전하면") {
            lateinit var after: UserPoint
            beforeTest {
                every { userPointTable.selectById(1L) } returns UserPoint(1, 0, 10L)
                every { userPointTable.insertOrUpdate(1L, 100L) } returns UserPoint(1, 100, 20L)
                every {
                    pointHistoryTable.insert(1L, 100L, TransactionType.CHARGE, 20L)
                } returns PointHistory(999, 1, TransactionType.CHARGE, 100, 20L)

                // ✅ 실제 호출은 테스트 실행 단계에서 수행
                after = service.charge(1L, 100L)
            }

            then("잔액은 100이어야 한다") {
                after.point shouldBe 100L
            }

            then("호출 순서: select -> update -> history.insert") {
                verifySequence {
                    userPointTable.selectById(1L)
                    userPointTable.insertOrUpdate(1L, 100L)
                    pointHistoryTable.insert(1L, 100L, TransactionType.CHARGE, 20L)
                }
                confirmVerified(userPointTable, pointHistoryTable)
            }
        }

        `when`("0원 이하를 충전하면") {
            then("IllegalArgumentException 이 발생해야 한다") {
                // 이 경우는 스텁이 필요 없고, 어떤 호출도 발생하지 않아야 함
                val r0 = runCatching { service.charge(10L, 0L) }
                val rNeg = runCatching { service.charge(10L, -1L) }

                r0.isFailure shouldBe true
                rNeg.isFailure shouldBe true
                r0.exceptionOrNull()!!::class shouldBe IllegalArgumentException::class
                rNeg.exceptionOrNull()!!::class shouldBe IllegalArgumentException::class

                verify(exactly = 0) { userPointTable.selectById(any()) }
                verify(exactly = 0) { userPointTable.insertOrUpdate(any(), any()) }
                verify(exactly = 0) { pointHistoryTable.insert(any(), any(), any(), any()) }
                confirmVerified(userPointTable, pointHistoryTable)
            }
        }
    }

    given("사용 로직") {

        `when`("잔액 100에서 60원을 사용하면") {
            lateinit var after: UserPoint
            beforeTest {
                every { userPointTable.selectById(20L) } returns UserPoint(20, 100, 100L)
                every { userPointTable.insertOrUpdate(20L, 40L) } returns UserPoint(20, 40, 300L)
                every {
                    pointHistoryTable.insert(20L, 60L, TransactionType.USE, 300L)
                } returns PointHistory(2, 20, TransactionType.USE, 60L, 300L)

                after = service.use(20L, 60L)
            }

            then("잔액은 40이어야 한다") {
                after.point shouldBe 40L
            }

            then("호출 순서 검증") {
                verifySequence {
                    userPointTable.selectById(20L)
                    userPointTable.insertOrUpdate(20L, 40L)
                    pointHistoryTable.insert(20L, 60L, TransactionType.USE, 300L)
                }
                confirmVerified(userPointTable, pointHistoryTable)
            }
        }

        `when`("잔액 50에서 80원을 사용하면") {
            beforeTest {
                every { userPointTable.selectById(21L) } returns UserPoint(21, 50, 100L)
            }
            then("IllegalArgumentException 이 발생해야 한다(잔액 부족)") {
                val r = runCatching { service.use(21L, 80L) }
                r.isFailure shouldBe true
                r.exceptionOrNull()!!::class shouldBe IllegalArgumentException::class

                verify(exactly = 1) { userPointTable.selectById(21L) }
                verify(exactly = 0) { userPointTable.insertOrUpdate(any(), any()) }
                verify(exactly = 0) { pointHistoryTable.insert(any(), any(), any(), any()) }
                confirmVerified(userPointTable, pointHistoryTable)
            }
        }

        `when`("사용 amount 가 0 이하이면") {
            then("IllegalArgumentException 이 발생해야 한다") {
                val r0 = runCatching { service.use(30L, 0L) }
                val rNeg = runCatching { service.use(30L, -10L) }

                r0.isFailure shouldBe true
                rNeg.isFailure shouldBe true
                r0.exceptionOrNull()!!::class shouldBe IllegalArgumentException::class
                rNeg.exceptionOrNull()!!::class shouldBe IllegalArgumentException::class

                verify(exactly = 0) { userPointTable.selectById(any()) }
                verify(exactly = 0) { userPointTable.insertOrUpdate(any(), any()) }
                verify(exactly = 0) { pointHistoryTable.insert(any(), any(), any(), any()) }
                confirmVerified(userPointTable, pointHistoryTable)
            }
        }
    }

    given("히스토리 조회") {
        `when`("특정 유저의 히스토리를 요청하면") {
            lateinit var histories: List<PointHistory>
            beforeTest {
                every { pointHistoryTable.selectAllByUserId(1L) } returns listOf(
                    PointHistory(10, 1, TransactionType.CHARGE, 100, 111L),
                    PointHistory(11, 1, TransactionType.USE, 40, 222L),
                )
                histories = service.getHistories(1L)
            }

            then("리스트를 그대로 반환해야 한다") {
                histories shouldHaveSize 2
                histories[0].type shouldBe TransactionType.CHARGE
                histories[1].type shouldBe TransactionType.USE
                verify(exactly = 1) { pointHistoryTable.selectAllByUserId(1L) }
                confirmVerified(pointHistoryTable)
            }
        }
    }
})
