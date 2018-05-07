package ru.nchernetsov.IoT;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import scala.concurrent.duration.FiniteDuration;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class DeviceGroupQuery extends AbstractActor {
    public static final class CollectionTimeout {
    }

    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    final Map<ActorRef, String> actorToDeviceId;
    final long requestId;
    final ActorRef requester;

    Cancellable queryTimeoutTimer;

    public DeviceGroupQuery(Map<ActorRef, String> actorToDeviceId, long requestId, ActorRef requester, FiniteDuration timeout) {
        this.actorToDeviceId = actorToDeviceId;
        this.requestId = requestId;
        this.requester = requester;

        queryTimeoutTimer = getContext().getSystem().scheduler().scheduleOnce(
            timeout, getSelf(), new CollectionTimeout(), getContext().dispatcher(), getSelf());
    }

    public static Props props(Map<ActorRef, String> actorToDeviceId, long requestId, ActorRef requester, FiniteDuration timeout) {
        return Props.create(DeviceGroupQuery.class, actorToDeviceId, requestId, requester, timeout);
    }

    @Override
    public void preStart() throws Exception {
        for (ActorRef deviceActor : actorToDeviceId.keySet()) {
            getContext().watch(deviceActor);
            deviceActor.tell(new Device.ReadTemperature(0L), getSelf());
        }
    }

    @Override
    public void postStop() throws Exception {
        queryTimeoutTimer.cancel();
    }

    @Override
    public Receive createReceive() {
        return waitingForReplies(new HashMap<>(), actorToDeviceId.keySet());
    }

    public Receive waitingForReplies(
        Map<String, DeviceGroup.TemperatureReading> repliesSoFar, Set<ActorRef> stillWaiting) {

        return receiveBuilder()
            .match(Device.RespondTemperature.class, r -> {
                ActorRef deviceActor = getSender();
                DeviceGroup.TemperatureReading reading = r.value
                    .map(v -> (DeviceGroup.TemperatureReading) new DeviceGroup.Temperature(v))
                    .orElse(new DeviceGroup.TemperatureNotAvailable());
                receivedResponse(deviceActor, reading, stillWaiting, repliesSoFar);
            })
            .build();
    }

    private void receivedResponse(ActorRef deviceActor, DeviceGroup.TemperatureReading reading, Set<ActorRef> stillWaiting, Map<String, DeviceGroup.TemperatureReading> repliesSoFar) {
    }
}
