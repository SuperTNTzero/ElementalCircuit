package dev.elementalcircuits.client.editor;

import dev.elementalcircuits.adapter.BlueprintTextCode;
import dev.elementalcircuits.domain.*;

/** Subpage state and pure validation. No clipboard, widgets, network or live circuit references. */
public final class BlueprintTransferModel {
    public boolean exporting;
    public String input="",output="",message="粘贴蓝图码，载入法阵";
    public String text(){return exporting?output:input;}
    public void text(String text){if(exporting)output=text;else input=text;}
    public String export(Blueprint draft){output=BlueprintTextCode.encode(draft);return output;}
    public Blueprint imported(Rules carrier){
        Blueprint decoded=BlueprintTextCode.decode(input);
        return new Blueprint(1,carrier,decoded.runes(),decoded.routes(),decoded.continuous());
    }
}
