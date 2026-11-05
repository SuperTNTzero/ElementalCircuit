package dev.elementalcircuits.client.editor;

import dev.elementalcircuits.adapter.codec.BlueprintTextCode;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.circuit.Blueprint;
import dev.elementalcircuits.domain.circuit.Rules;

/** Subpage state and pure validation. No clipboard, widgets, network or live circuit references. */
public final class BlueprintTransferModel {
    public boolean exporting;
    public String input = "",
            output = "",
            message =
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.editor._blueprint_transfer_model.001");

    public String text() {
        return exporting ? output : input;
    }

    public void text(String text) {
        if (exporting) output = text;
        else input = text;
    }

    public String export(Blueprint draft) {
        output = BlueprintTextCode.encode(draft);
        return output;
    }

    public Blueprint imported(Rules carrier) {
        Blueprint decoded = BlueprintTextCode.decode(input);
        return new Blueprint(
                decoded.version(),
                carrier,
                decoded.runes(),
                decoded.routes(),
                decoded.continuous());
    }
}
