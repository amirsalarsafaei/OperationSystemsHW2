package DeadLock;

public enum DeadLockHandlingType {
    prevention, detection, none;
    public static DeadLockHandlingType convert(String input) {
        return switch (input) {
            case "PREVENT" -> prevention;
            case "DETECT" -> detection;
            case "NONE" -> none;
            default -> null;
        };
    }
}
