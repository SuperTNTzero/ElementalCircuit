package dev.elementalcircuits.client.effect;

/** Fixed-client-step cosmetic impulse. Repeated hits merge; pauses and logout are explicit. */
public final class FeedbackPulse {
    private int castAge = 10, hitAge = 10;
    private float power = 1;

    public void accept(boolean cast, boolean hit, float strength) {
        if (!Float.isFinite(strength) || strength <= 0 || strength > 2)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.effect._feedback_pulse.001"));
        power = strength;
        if (cast && castAge >= 4) castAge = 0;
        if (hit && hitAge >= 4) hitAge = 0;
    }

    public void tick() {
        castAge = Math.min(10, castAge + 1);
        hitAge = Math.min(10, hitAge + 1);
    }

    public void clear() {
        castAge = hitAge = 10;
        power = 1;
    }

    public float marker(float partial) {
        return Math.max(0, 1 - (hitAge + partial) / 8f);
    }

    public float recoil(float partial) {
        double age = castAge + partial;
        return age >= 8 ? 0 : (float) (Math.sin(age * Math.PI / 8) * Math.exp(-age * .24) * power);
    }

    public float kick(float partial) {
        double age = hitAge + partial;
        return age >= 6 ? 0 : (float) (Math.sin(age * Math.PI / 3) * Math.exp(-age * .45) * power);
    }

    public float roll(float partial) {
        return kick(partial) * Math.min(1, recoil(partial) * 2);
    }
}
