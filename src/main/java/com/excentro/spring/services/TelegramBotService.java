package com.excentro.spring.services;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.List;

import com.excentro.spring.bot.AccessLevelValidator;
import com.excentro.spring.bot.TelegramWebHookBot;
import com.excentro.spring.config.TelegramBotConfig;
import com.github.unafraid.telegrambot.handlers.ICommandHandler;
import com.github.unafraid.telegrambot.handlers.ITelegramHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.updates.SetWebhook;
import org.telegram.telegrambots.meta.api.objects.ApiResponse;
import org.telegram.telegrambots.meta.api.objects.WebhookInfo;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;

/**
 * @author UnAfraid
 */
@Service
public class TelegramBotService extends TelegramWebHookBot {
	private final String webPath;
	private final TelegramBotConfig config;
	
	public TelegramBotService(TelegramBotConfig config,
							  ApplicationContext appContext,
							  ObjectProvider<DefaultBotOptions> defaultBotOptions,
							  AccessLevelValidator accessLevelValidator) throws Exception {
		super(config.getToken(), config.getUsername(), appContext, defaultBotOptions, accessLevelValidator);
		this.config = config;
		this.webPath = config.getUrl();
		init();
	}
	
	private void init() throws Exception {
		final WebhookInfo info = getWebhookInfo();
		final String url = info.getUrl();
		final String webHookUrl = computeCallbackEndpoint();
		
		if (url == null || url.isEmpty() || !url.equals(webHookUrl) || info.getMaxConnections() != config.getMaxConnections()) {
			final SetWebhook setWebhook = new SetWebhook();
			setWebhook.setUrl(webHookUrl);
			setWebhook.setMaxConnections(config.getMaxConnections());
			setWebhook(setWebhook);
		}
		
		registerMyCommands();
	}
	
	private String computeCallbackEndpoint() {
		final StringBuilder sb = new StringBuilder(config.getUrl());
		if (sb.charAt(sb.length() - 1) != '/') {
			sb.append('/');
		}
		sb.append("callback/");
		sb.append(config.getToken());
		return sb.toString();
	}
	
	private void registerMyCommands() throws TelegramApiException {
		final List<BotCommand> botCommandList = new ArrayList<>();
		for (ITelegramHandler handler : getHandlers()) {
			if (handler instanceof ICommandHandler) {
				final ICommandHandler commandHandler = (ICommandHandler) handler;
				botCommandList.add(new BotCommand(commandHandler.getCommand(), commandHandler.getDescription()));
			}
		}
		
		if (!botCommandList.isEmpty()) {
			execute(new SetMyCommands(botCommandList, new BotCommandScopeDefault(), config.getLanguageCode()));
		}
	}
	
	/**
	 * If DefaultBotOptions has proxy settings they will be applied as RequestFactory on the RestTemplate
	 *
	 * @return RestTemplate
	 */
	private RestTemplate newRestTemplate() {
		final RestTemplate rest = new RestTemplate();
		final DefaultBotOptions options = getOptions();
		switch (options.getProxyType()) {
			case HTTP: {
				final SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
				requestFactory.setProxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(options.getProxyHost(), options.getProxyPort())));
				rest.setRequestFactory(requestFactory);
				break;
			}
			case SOCKS4:
			case SOCKS5: {
				final SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
				requestFactory.setProxy(new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(options.getProxyHost(), options.getProxyPort())));
				rest.setRequestFactory(requestFactory);
				break;
			}
		}
		return rest;
	}
	
	@Override
	public void setWebhook(SetWebhook setWebhook) throws TelegramApiException {
		try {
			final RestTemplate rest = newRestTemplate();
			final HttpHeaders headers = new HttpHeaders();
			headers.add("Content-Type", "application/json");
			headers.add("Accept", "application/json");
			
			final String setWebhookUrl = String.format("https://api.telegram.org/bot%s/%s", getBotToken(), SetWebhook.PATH);
			rest.exchange(setWebhookUrl, HttpMethod.POST, new HttpEntity<>(setWebhook, headers), ApiResponse.class);
		} catch (Exception e) {
			throw new TelegramApiRequestException("Error executing setWebHook method", e);
		}
	}
	
	@Override
	public String getBotPath() {
		return webPath;
	}
}
