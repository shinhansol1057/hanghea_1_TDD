package io.hhplus.tdd.point

import io.hhplus.tdd.database.PointHistoryTable
import io.hhplus.tdd.database.UserPointTable
import org.springframework.stereotype.Service

@Service
class PointService(
    private val userPointTable: UserPointTable,
    private val pointHistoryTable: PointHistoryTable,
) {
    fun getPoint(userId: Long): UserPoint =
        userPointTable.selectById(userId)

    fun getHistories(userId: Long): List<PointHistory> =
        pointHistoryTable.selectAllByUserId(userId)

    fun charge(userId: Long, amount: Long): UserPoint {
        if (amount <= 0) throw IllegalArgumentException("충전 금액은 0보다 커야 합니다: $amount")
        val current = userPointTable.selectById(userId)
        val after = userPointTable.insertOrUpdate(userId, current.point + amount)
        pointHistoryTable.insert(userId, amount, TransactionType.CHARGE, after.updateMillis)
        return after
    }

    fun use(userId: Long, amount: Long): UserPoint {
        if (amount <= 0) throw IllegalArgumentException("사용 금액은 0보다 커야 합니다: $amount")
        val current = userPointTable.selectById(userId)
        if (current.point < amount) throw IllegalArgumentException("잔액이 부족합니다: 현재 잔액=${current.point}, 사용 금액=$amount")
        val after = userPointTable.insertOrUpdate(userId, current.point - amount)
        pointHistoryTable.insert(userId, amount, TransactionType.USE, after.updateMillis)
        return after
    }
}
