package indi.etern.musichud.client.ui.render;

import indi.etern.musichud.client.audio.SpectrumSnapshot;

public final class AudioReactiveScale {
    private AudioReactiveScale() {
    }

    public static float compute(SpectrumSnapshot spectrum, double amount,
                                double lowWeight, double midWeight) {
        if (spectrum == null || amount <= 1e-4) {
            return 1f;
        }
        float energy = (float) (spectrum.low() * lowWeight + spectrum.mid() * midWeight);
        energy = Math.clamp(energy, 0f, 1f);
        return 1f + energy * (float) amount;
    }
}
