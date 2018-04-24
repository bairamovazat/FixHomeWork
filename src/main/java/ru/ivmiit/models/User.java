package ru.ivmiit.models;

import lombok.*;
import ru.ivmiit.security.role.Role;
import ru.ivmiit.security.states.State;

import javax.persistence.*;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "chats")
@Entity
@Table(name = "messenger_user")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(unique = true)
    private String login;
    private String hashPassword;
    private String name;

    @ManyToMany(mappedBy = "members", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<Chat> chats;

    private UUID uuid;
    private String status;
    private String phone;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Enumerated(EnumType.STRING)
    private State state;

    private String hashTempPassword;

    private String email;
}
