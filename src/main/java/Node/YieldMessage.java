package Node;

public class YieldMessage extends SimpleTargetableMessage {
    public YieldMessage() {
    }

    public YieldMessage(int sourceUID, int target) {
        super(sourceUID, target);
    }

    @Override
    public String toString() {
        return "YieldMessage{} " + super.toString();
    }
}
