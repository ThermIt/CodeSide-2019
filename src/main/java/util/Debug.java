package util;

import java.io.IOException;
import java.io.OutputStream;

public class Debug {

    private OutputStream stream;
    private boolean enabledDraw;
    private boolean enabledOutput;

    public Debug(OutputStream stream) {
        this.stream = stream;
    }

    public void enableDraw() {
        enabledDraw = true;
    }

    public void enableOutput() {
        enabledDraw = true;
    }

    public void disableDraw() {
        enabledDraw = false;
    }

    public void disableOutput() {
        enabledDraw = false;
    }

    public boolean isEnabledDraw() {
        return enabledDraw;
    }

    public boolean isEnabledOutput() {
        return enabledOutput;
    }

    public void draw(model.CustomData customData) {
        if (enabledDraw) {
            try {
                new model.PlayerMessageGame.CustomDataMessage(customData).writeTo(stream);
                stream.flush();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}