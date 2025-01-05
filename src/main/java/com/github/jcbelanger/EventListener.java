package com.github.jcbelanger;

import discord4j.core.event.domain.Event;
import org.slf4j.Logger;
import reactor.core.publisher.Mono;

public interface EventListener<T extends Event> {

    Logger log = org.slf4j.LoggerFactory.getLogger(EventListener.class);

    Class<T> getEventType();

    Mono<Void> execute(T event);

    default Mono<Void> handleError(Throwable error) {
        log.error("Unable to process event: {}", getEventType().getSimpleName(), error);
        return Mono.empty();
    }
}