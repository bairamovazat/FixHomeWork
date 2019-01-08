package ru.ivmiit.controlers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;
import ru.ivmiit.forms.AddChatForm;
import ru.ivmiit.forms.SendMessageForm;
import ru.ivmiit.models.Chat;
import ru.ivmiit.models.Message;
import ru.ivmiit.models.User;
import ru.ivmiit.repositories.ChatsRepository;
import ru.ivmiit.repositories.MessagesRepository;
import ru.ivmiit.repositories.UsersRepository;
import ru.ivmiit.services.AuthenticationService;
import ru.ivmiit.services.ChatService;
import ru.ivmiit.services.FileService;
import ru.ivmiit.transfer.ChatDto;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;


@RestController
@RequestMapping("/api")
public class ApiController {

    @Autowired
    private AuthenticationService service;

    @Autowired
    private FileService fileService;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private ChatsRepository chatsRepository;

    @Autowired
    private MessagesRepository messagesRepository;

    @Autowired
    private ChatService chatService;


    @GetMapping("/chats")
    public List<ChatDto> getAllChats(Authentication authentication){
        User user = service.getUserByAuthentication(authentication);
        List<Chat> chats = chatsRepository.findByMembersContains(user);
        chatService.keepFirstMessageInChats(chats);
        return ChatDto.from(chats);
    }

    @GetMapping("/chat/{chatId}")
    public ResponseEntity<ChatDto> getChatPage(@PathVariable("chatId") Long chatId,
                                      Authentication authentication){
        User user = service.getUserByAuthentication(authentication);
        Optional<Chat> chat = chatsRepository.findByMembersContainsAndId(user, chatId);
        if (!chat.isPresent()) {
            return  ResponseEntity.badRequest().body(null);
        }
        return ResponseEntity.ok(ChatDto.from(chat.get()));
    }


    @PostMapping("/chats/add")
    public ResponseEntity addChat(@ModelAttribute("userForm") AddChatForm addChatForm,
                          Authentication authentication) {
        User user = service.getUserByAuthentication(authentication);
        Chat chat = Chat.builder()
                .name(addChatForm.getName())
                .members(Collections.singletonList(user))
                .owner(user)
                .build();
        chatsRepository.save(chat);
        return ResponseEntity.ok(null);
    }


    @PostMapping("/chat/send")
    public ResponseEntity<String> sendMessage(@RequestBody SendMessageForm sendMessageForm,
                                              Authentication authentication) throws IllegalArgumentException{
        User user = service.getUserByAuthentication(authentication);
        Optional<Chat> chat = chatsRepository.findByMembersContainsAndId(user, sendMessageForm.getChatId());
        if (!chat.isPresent()) {
            throw new IllegalArgumentException("bad chat id");
        }else if(sendMessageForm.getMessage().isEmpty()){
            throw new IllegalArgumentException("message is empty");
        }

        Message message = Message.builder()
                .user(user)
                .text(sendMessageForm.getMessage())
                .sendDate(new Date())
                .isRead(false)
                .chat(chat.get())
                .build();

        messagesRepository.save(message);

        return ResponseEntity.ok(null);
    }

}
