package com.idletextnotifier;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.InteractingChanged;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static net.runelite.api.AnimationID.IDLE;


@Slf4j
@PluginDescriptor(
	name = "Idle Text Notification"
)
public class IdleTextNotifier extends Plugin
{

	private static final Duration SIX_HOUR_LOGOUT_WARNING_AFTER_DURATION = Duration.ofMinutes(340);
	private static final String FISHING_SPOT = "Fishing spot";


	@Inject
	private Client client;

	@Inject
	private Notifier notifier;

	@Inject
	private IdleTextNotifierConfig config;


	private int lastCombatCountdown = 0;
	private Instant lastAnimating;
	private int lastAnimation = IDLE;
	private Instant lastInteracting;
	private Actor lastInteract;
	private boolean ready;
	private Instant sixHourWarningTime;
	private boolean lastInteractWasCombat;




	@Subscribe
	public void onGameTick(GameTick event)
	{
		if(config.accountSID().trim().isEmpty()
				|| config.authToken().trim().isEmpty()
				|| config.twilioNumber().trim().isEmpty()
				|| config.targetNumber().trim().isEmpty()){
			log.info("Missing Parameters");
			return;
		}

		final Player local = client.getLocalPlayer();
		final Duration waitDuration = Duration.ofMillis(5000);
		lastCombatCountdown = Math.max(lastCombatCountdown - 1, 0);

		//log.info("Checking GameState");
		if (client.getGameState() != GameState.LOGGED_IN
				|| local == null
				// If user has clicked in the last second then they're not idle so don't send idle notification
				|| System.currentTimeMillis() - client.getMouseLastPressedMillis() < 1000
				|| client.getKeyboardIdleTicks() < 10)
		{
			resetTimers();
			return;
		}

//		if (config.logoutIdle() && checkIdleLogout())
//		{
//			notifier.notify("You are about to log out from idling too long!");
//		}
//
//		if (check6hrLogout())
//		{
//			notifier.notify("You are about to log out from being online for 6 hours!");
//		}
//
//		if (config.animationIdle() && checkAnimationIdle(waitDuration, local))
//		{
//			notifier.notify("You are now idle!");
//		}
//
//		if (config.movementIdle() && checkMovementIdle(waitDuration, local))
//		{
//			notifier.notify("You have stopped moving!");
//		}

		if (checkInteractionIdle(waitDuration, local))
		{
			notifier.notify("You are now out of combat!");
			log.info("Connecting to Twilio...");
			try {
				Twilio.init(config.accountSID(), config.authToken());
				log.info("Connected to Twilio.");
			}
			catch(Exception e){
				log.error(e.toString());
				return;
			}

			log.info("Attempting to send message...");
			Message message = Message.creator(new PhoneNumber(config.targetNumber()),
					new PhoneNumber(config.twilioNumber()),
					"You are idle!").create();
			log.info("Message sent.");
		}
	}

	@Override
	protected void startUp() throws Exception
	{
		log.info("Starting Idle Text Notification Plugin.");
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("Idle Text Notification stopped!");
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		log.info("onGameStateChanged Called.");
		lastInteracting = null;

		GameState state = gameStateChanged.getGameState();

		switch (state)
		{
			case LOGIN_SCREEN:
				resetTimers();
				break;
			case LOGGING_IN:
			case HOPPING:
			case CONNECTION_LOST:
				ready = true;
				break;
			case LOGGED_IN:
				if (ready)
				{
					sixHourWarningTime = Instant.now().plus(SIX_HOUR_LOGOUT_WARNING_AFTER_DURATION);
					ready = false;
					resetTimers();
				}

				break;
		}
	}

	private void resetTimers()
	{
		final Player local = client.getLocalPlayer();

		// Reset animation idle timer
		lastAnimating = null;
		if (client.getGameState() == GameState.LOGIN_SCREEN || local == null || local.getAnimation() != lastAnimation)
		{
			lastAnimation = IDLE;
		}

		// Reset interaction idle timer
		lastInteracting = null;
		if (client.getGameState() == GameState.LOGIN_SCREEN || local == null || local.getInteracting() != lastInteract)
		{
			lastInteract = null;
		}
	}

	private boolean checkInteractionIdle(Duration waitDuration, Player local)
	{
		if (lastInteract == null)
		{
			return false;
		}

		final Actor interact = local.getInteracting();

		if (interact == null)
		{
			if (lastInteracting != null
					&& Instant.now().compareTo(lastInteracting.plus(waitDuration)) >= 0
					&& lastCombatCountdown == 0)
			{
				lastInteract = null;
				lastInteracting = null;

				// prevent animation notifications from firing too
				lastAnimation = IDLE;
				lastAnimating = null;

				return true;
			}
		}
		else
		{
			lastInteracting = Instant.now();
		}
		return false;
	}

	@Subscribe
	public void onInteractingChanged(InteractingChanged event)
	{
		final Actor source = event.getSource();
		if (source != client.getLocalPlayer())
		{
			return;
		}

		final Actor target = event.getTarget();

		// Reset last interact
		if (target != null)
		{
			lastInteract = null;
		}
		else
		{
			lastInteracting = Instant.now();
		}

		final boolean isNpc = target instanceof NPC;

		// If this is not NPC, do not process as we are not interested in other entities
		if (!isNpc)
		{
			return;
		}

		final NPC npc = (NPC) target;
		final NPCComposition npcComposition = npc.getComposition();
		final List<String> npcMenuActions = Arrays.asList(npcComposition.getActions());

		if (npcMenuActions.contains("Attack"))
		{
			// Player is most likely in combat with attack-able NPC
			resetTimers();
			lastInteract = target;
			lastInteracting = Instant.now();
			lastInteractWasCombat = true;
		}
		else if (target.getName() != null && target.getName().contains(FISHING_SPOT))
		{
			// Player is fishing
			resetTimers();
			lastInteract = target;
			lastInteracting = Instant.now();
			lastInteractWasCombat = false;
		}
	}

	@Provides
	IdleTextNotifierConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(IdleTextNotifierConfig.class);
	}
}
