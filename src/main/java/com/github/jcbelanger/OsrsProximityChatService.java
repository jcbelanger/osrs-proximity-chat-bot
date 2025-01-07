package com.github.jcbelanger;

import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.event.domain.Event;
import discord4j.core.event.domain.VoiceStateUpdateEvent;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.lifecycle.ResumeEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.CategorizableChannel;
import discord4j.core.object.entity.channel.GuildMessageChannel;
import discord4j.core.object.entity.channel.VoiceChannel;
import discord4j.core.retriever.EntityRetrievalStrategy;
import discord4j.core.spec.GuildMemberEditSpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class OsrsProximityChatService {

    @Value("${discord.token}")
    private String token;

    @Value("${discord.application}")
    private String applicationID;

    @Value("${discord.guild}")
    private String guildID;

    public Mono<Void> proximityChat() {
        var client = DiscordClientBuilder.create(token).build();

        var login = client.withGateway(gateway -> {
            var waitingRoomId = Snowflake.of("1325960465939038320");
            var generalChatId = Snowflake.of("1324842840278302746");

            var userJoin = gateway.on(VoiceStateUpdateEvent.class)
                .filter(VoiceStateUpdateEvent::isJoinEvent)
                .map(VoiceStateUpdateEvent::getCurrent)
                .filter(voiceState -> voiceState.getChannelId()
                    .filter(waitingRoomId::equals)
                    .isPresent())
                .flatMap(voiceState -> voiceState.getMember(EntityRetrievalStrategy.STORE_FALLBACK_REST))
                .doOnNext(member -> log.info("Member joined waiting room: {}", member.getUsername()));

            var checkWaiting = gateway.on(ReadyEvent.class).cast(Event.class)
                    .mergeWith(gateway.on(ResumeEvent.class).cast(Event.class));

            var usersWaiting = checkWaiting
                .doOnNext(event -> log.info("Re-sync members after: {}...", event.getClass().getSimpleName()))
                    .checkpoint()
                .flatMap(event -> gateway.getChannelById(waitingRoomId).cast(VoiceChannel.class))
                .flatMap(GuildMessageChannel::getMembers)
                .doOnNext(member -> log.info("Found member: {}", member.getUsername()));

            var usersToMove = usersWaiting.mergeWith(userJoin);

            var movedUsers = usersToMove
                .doOnNext(member -> log.info("Moving member to general chat: {}", member.getUsername()))
                .flatMap(member -> member.edit(GuildMemberEditSpec.builder()
                    .newVoiceChannelOrNull(generalChatId)
                    .build()));

            var userLeaves = gateway.on(VoiceStateUpdateEvent.class)
                .filter(VoiceStateUpdateEvent::isLeaveEvent)
                .filterWhen(event -> event.getCurrent().getChannel(EntityRetrievalStrategy.STORE_FALLBACK_REST)
                    .flatMap(CategorizableChannel::getCategory)
                    .map(cat -> "Proximity Chat Rooms".equals(cat.getName()))
                    .defaultIfEmpty(false));

            var messageCreate = gateway.on(MessageCreateEvent.class, event -> {
                log.info("Message Create: {}", event);
                return Mono.just(event.getMessage())
                    .filter(message -> message.getAuthor().map(user -> !user.isBot()).orElse(false))
                    .filter(message -> message.getContent().equalsIgnoreCase("!todo"))
                    .flatMap(Message::getChannel)
                    .flatMap(channel -> channel.createMessage("Things to do today asdf:\n - write a bot\n - eat lunch\n - play a game"));
            }).then();

            return Mono.empty()
                .and(movedUsers)
                .and(userLeaves)
                .and(messageCreate);
        });

        return login.doOnError(error -> log.error("Error", error));
    }
}
