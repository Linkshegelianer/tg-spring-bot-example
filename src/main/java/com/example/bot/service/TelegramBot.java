package com.example.bot.service;

import java.io.File;
import java.util.*;
import com.example.bot.config.BotConfig;
import com.example.bot.model.Joke;
import com.example.bot.model.JokeRepository;
import com.example.bot.model.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.vdurmont.emoji.EmojiParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Slf4j
@Component
public class TelegramBot extends TelegramLongPollingBot {

    final BotConfig config;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JokeRepository jokeRepository;

    static final int MAX_JOKE_ID_MINUS_ONE = 3772;
    static final String NEXT_JOKE = "NEXT_JOKE";

    static final String ERROR_TEXT = "Error occurred: ";

    static final String HELP_TEXT =
            "This is an example bot based on Spring Boot framework.\n" +
                    "You can execute commands from the main menu on the left or by typing a command:\n" +
                    "/start to see a welcome message\n" +
                    "/mydata to see your stored data\n" +
                    "/deletedata to delete your data from the bot\n" +
                    "/settings to set your preferences\n" +
                    "/easteregg to see an easter egg in the bot\n";

    public TelegramBot(BotConfig botConfig) {
        this.config = botConfig;
        List<BotCommand> listOfCommands = new ArrayList<>();
        listOfCommands.add(new BotCommand("/start", "get a welcome message"));
        listOfCommands.add(new BotCommand("/joke", "get a random joke"));
        listOfCommands.add(new BotCommand("/mydata", "get your stored data"));
        listOfCommands.add(new BotCommand("/deletedata", "delete your data"));
        listOfCommands.add(new BotCommand("/help", "info how to use this bot"));
        listOfCommands.add(new BotCommand("/settings", "set your preferences"));
        listOfCommands.add(new BotCommand("/easteregg", "see a little easter egg!"));
        try {
            this.execute(new SetMyCommands(listOfCommands, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            log.error(Arrays.toString(e.getStackTrace()));
        }
    }

    @Override
    public String getBotUsername() {
        return config.getBotName();
    }

    @Override
    public String getBotToken() { return config.getToken(); }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            switch (messageText) {
                case "/start" -> {
                    showStart(chatId, update.getMessage().getChat().getFirstName());
                    try {
                        ObjectMapper objectMapper = new ObjectMapper();
                        TypeFactory typeFactory = objectMapper.getTypeFactory();
                        List<Joke> jokeList = objectMapper.readValue(new File("db/stupidstuff.json"),
                                typeFactory.constructCollectionType(List.class, Joke.class));
                        jokeRepository.saveAll(jokeList);
                    } catch (Exception e) {
                        log.error(Arrays.toString(e.getStackTrace()));
                    }
                }


                case "/help" ->
                    sendMessage(HELP_TEXT, chatId);

                case "/joke" -> {

                    var joke = getRandomJoke();

                    joke.ifPresent(randomJoke -> addButtonAndSendMessage(randomJoke.getBody(), chatId));


                }
                default -> commandNotFound(chatId);
            }
        }

        else if(update.hasCallbackQuery()){

            String callbackData = update.getCallbackQuery().getData();
            long chatId = update.getCallbackQuery().getMessage().getChatId();

            if(callbackData.equals(NEXT_JOKE)) {

                var joke = getRandomJoke();

                //joke.ifPresent(randomJoke -> addButtonAndSendMessage(randomJoke.getBody(), chatId));

                joke.ifPresent(randomJoke -> addButtonAndEditText(randomJoke.getBody(), chatId, update.getCallbackQuery().getMessage().getMessageId()));

            }

        }
    }

    private void showStart(long chatId, String name) {
        String answer = EmojiParser.parseToUnicode(
                "Hi, " + name + "! :smile:" + " Nice to meet you! I am a Simple Random Joke Bot created by Dmitrijs Finaskins from proj3c.io \n");
        sendMessage(answer, chatId);
    }

    private void sendMessage(String textToSend, long chatId) {
        SendMessage message = new SendMessage(); // Create a message object object
        message.setChatId(String.valueOf(chatId));
        message.setText(textToSend);
        send(message);
    }

    private void send(SendMessage msg) {
        try {
            execute(msg); // Sending our message object to user
        } catch (TelegramApiException e) {
            log.error(Arrays.toString(e.getStackTrace()));
        }
    }

//    private void executeMessage(SendMessage message){
//        try {
//            execute(message);
//        } catch (TelegramApiException e) {
//            log.error(ERROR_TEXT + e.getMessage());
//        }
//    }

    private Optional<Joke> getRandomJoke(){
        var r = new Random();
        var randomId = r.nextInt(MAX_JOKE_ID_MINUS_ONE) + 1;

        return jokeRepository.findById(randomId);
    }

    private void addButtonAndSendMessage(String joke, long chatId){

        SendMessage message = new SendMessage();
        message.setText(joke);
        message.setChatId(chatId);

        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
        List<InlineKeyboardButton> rowInline = new ArrayList<>();
        var inlinekeyboardButton = new InlineKeyboardButton();
        inlinekeyboardButton.setCallbackData(NEXT_JOKE);
        inlinekeyboardButton.setText(EmojiParser.parseToUnicode("next joke " + ":rolling_on_the_floor_laughing:"));
        rowInline.add(inlinekeyboardButton);
        rowsInline.add(rowInline);
        markupInline.setKeyboard(rowsInline);
        message.setReplyMarkup(markupInline);
        send(message);

    }

    private void addButtonAndEditText(String joke, long chatId, Integer messageId){

        EditMessageText message = new EditMessageText();
        message.setChatId(chatId);
        message.setText(joke);
        message.setMessageId(messageId);

        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
        List<InlineKeyboardButton> rowInline = new ArrayList<>();
        var inlinekeyboardButton = new InlineKeyboardButton();
        inlinekeyboardButton.setCallbackData(NEXT_JOKE);
        inlinekeyboardButton.setText(EmojiParser.parseToUnicode("next joke " + ":rolling_on_the_floor_laughing:"));
        rowInline.add(inlinekeyboardButton);
        rowsInline.add(rowInline);
        markupInline.setKeyboard(rowsInline);
        message.setReplyMarkup(markupInline);

        sendEditMessageText(message);
    }

    private void commandNotFound(long chatId) {

        String answer = EmojiParser.parseToUnicode(
                "Command not recognized, please verify and try again :stuck_out_tongue_winking_eye: ");
        sendMessage(answer, chatId);
    }

    private void sendEditMessageText(EditMessageText msg) {
        try {
            execute(msg); // Sending our message object to user
        } catch (TelegramApiException e) {
            log.error(Arrays.toString(e.getStackTrace()));
        }
    }
}
