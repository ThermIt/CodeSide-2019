package util;

import java.io.IOException;
import java.io.OutputStream;

public class Debug {
    private OutputStream stream;

    private boolean enabled;

    public Debug(OutputStream stream) {
        this.stream = stream;
    }

    public void enable() {
        enabled = true;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void draw(model.CustomData customData) {
        if (enabled) {
            try {
                new model.PlayerMessageGame.CustomDataMessage(customData).writeTo(stream);
                stream.flush();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}