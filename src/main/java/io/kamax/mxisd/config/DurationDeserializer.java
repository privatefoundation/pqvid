package io.kamax.mxisd.config;

public class DurationDeserializer {

    public long deserialize(String argument) {
        long duration = 0L;
        for (String part : argument.split(" ")) {
            String unit = part.substring(part.length() - 1);
            long value = Long.parseLong(part.substring(0, part.length() - 1));
            switch (unit) {
                case "s":
                    duration += value;
                    break;
                case "m":
                    duration += value * 60;
                    break;
                case "h":
                    duration += value * 60 * 60;
                    break;
                case "d":
                    duration += value * 60 * 60 * 24;
                    break;
                default:
                    throw new IllegalArgumentException(String.format("Unknown duration unit: %s", unit));
            }
        }

        return duration;
    }
}
