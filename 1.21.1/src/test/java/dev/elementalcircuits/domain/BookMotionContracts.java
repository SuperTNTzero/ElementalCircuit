package dev.elementalcircuits.domain;

import dev.elementalcircuits.client.book.BookMotion;

/** Animation continuity, bounded replacement and tick ownership independent of the renderer. */
public final class BookMotionContracts {
    private static int checks;

    private static void check(boolean value, String why) {
        checks++;
        if (!value) throw new AssertionError(why);
    }

    public static int run() {
        var motion = new BookMotion();
        check(motion.sample(0).open() == 0 && motion.sample(1).turn() < 0, "新物品没有旧动画");
        for (int i = 0; i < 12; i++) {
            float before = motion.sample(1).open();
            motion.tick(true);
            check(motion.sample(0).open() == before, "固定步边界连续");
            check(
                    motion.sample(.5f).open() >= before
                            && motion.sample(.5f).open() <= motion.sample(1).open(),
                    "开封单调且帧间插值有界");
        }
        check(motion.sample(1).open() == 1, "十步左右完整翻开");
        motion.flip(1);
        for (int tick = 0; tick < 6; tick++) motion.tick(true);
        check(motion.sample(.5f).turn() > .3 && motion.sample(.5f).turn() < .6, "翻动中页可取样");
        for (int i = 0; i < 10000; i++) motion.flip(i % 2 == 0 ? 1 : -1);
        for (int i = 0; i < 40; i++) {
            motion.tick(true);
            var pose = motion.sample(.8f);
            check(Float.isFinite(pose.open()) && pose.turn() <= 1, "密集换页只保留一个待播方向，状态有限");
        }
        check(motion.sample(1).turn() < 0, "密集换页最终静止，不积压一万次动画");
        for (int i = 0; i < 10; i++) motion.tick(false);
        check(motion.sample(1).open() == 0, "合书完整结束");
        motion.flip(-1);
        motion.tick(true);
        motion.reset();
        check(motion.sample(1).open() == 0 && motion.sample(1).turn() < 0, "换持物与退出清空所有状态");
        var a = motion.sample(.5f);
        for (int i = 0; i < 1000; i++) check(motion.sample(.5f).equals(a), "渲染采样不推进动画");
        boolean rejected = false;
        try {
            motion.sample(Float.NaN);
        } catch (IllegalArgumentException e) {
            rejected = true;
        }
        check(rejected, "拒绝非法插值");
        return checks;
    }
}
