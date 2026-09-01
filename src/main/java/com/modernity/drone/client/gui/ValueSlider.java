package com.modernity.drone.client.gui;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

final class ValueSlider extends AbstractSliderButton {
    private final double minimum;
    private final double maximum;
    private final DoubleConsumer setter;
    private final DoubleFunction<String> formatter;

    ValueSlider(
            int x,
            int y,
            int width,
            String label,
            double current,
            double minimum,
            double maximum,
            DoubleConsumer setter,
            DoubleFunction<String> formatter
    ) {
        super(x, y, width, 20, Component.empty(), normalized(current, minimum, maximum));
        this.minimum = minimum;
        this.maximum = maximum;
        this.setter = setter;
        this.formatter = value -> label + ": " + formatter.apply(value);
        updateMessage();
    }

    double actualValue() {
        return minimum + value * (maximum - minimum);
    }

    @Override
    protected void updateMessage() {
        if (formatter != null) {
            setMessage(Component.literal(formatter.apply(actualValue())));
        }
    }

    @Override
    protected void applyValue() {
        setter.accept(actualValue());
    }

    private static double normalized(double current, double minimum, double maximum) {
        if (!Double.isFinite(current) || maximum <= minimum) return 0.0;
        return Math.max(0.0, Math.min(1.0, (current - minimum) / (maximum - minimum)));
    }
}
