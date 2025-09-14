package com.mafuyu404.instantlyinteractinternally.api;

/**
 * FakeLevel 事务接口。
 * <p>基于整张虚拟层快照的“提交/回滚”语义，用于一组操作失败时快速恢复。
 * <p>建议使用 try-with-resources 自动回滚未提交的事务：
 *
 * <pre>
 * try (VirtualTransaction tx = FakeLevelAPI.beginTransaction(player)) {
 *     // 对虚拟层进行一组操作
 *     tx.commit(); // 若未调用，close() 会自动回滚
 * }
 * </pre>
 * <p>
 * 线程：服务器主线程；不要在异步线程中创建或操作事务。
 */
public interface VirtualTransaction extends AutoCloseable {
    /**
     * 标记事务成功结束。提交后事务进入非活动态，不再可回滚。
     */
    void commit();

    /**
     * 回滚事务：恢复快照。对已提交或已回滚的事务无效果（幂等）。
     */
    void rollback();

    /**
     * 事务是否仍处于活动态。提交或回滚后返回 false。
     */
    boolean isActive();

    @Override
    default void close() {
        // 默认 AutoCloseable：未 commit 时回滚
        if (isActive()) {
            rollback();
        }
    }

    /**
     * 空实现：用于占位（例如上下文不存在时返回 NOOP 以避免空指针分支复杂化）。
     */
    VirtualTransaction NOOP = new VirtualTransaction() {
        @Override
        public void commit() {
        }

        @Override
        public void rollback() {
        }

        @Override
        public boolean isActive() {
            return false;
        }

        @Override
        public void close() {
        }
    };
}