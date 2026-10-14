workspace "Instakill" "C4 model of Instakill as it exists today (Week 2 lab)" {

    model {
        user = person "User" "Registers, logs in, reads the feed, creates posts, likes and comments. Anonymous visitors can only log in or register."

        imageHosts = softwareSystem "External Image Hosts" "Any public URL (e.g. Unsplash). Instakill stores only the image URL; the browser loads the image directly." "External"

        instakill = softwareSystem "Instakill" "Instagram-style social feed. One Spring Boot monolith + Vue SPA + SQLite." {

            spa = container "Vue SPA" "Single-page app: login/register, feed (Newest / Trending), composer, likes, comments." "Vue 3, Vite dev server :5173, axios" "Browser"

            backend = container "Backend" "Serves the JSON REST API for the SPA and the server-rendered Thymeleaf pages. Single deployable." "Java 23, Spring Boot 3.3, port 8080" {

                group "Presentation" {
                    filters = component "HTTP Filter Chain" "RequestIdFilter adds X-Request-Id. Spring Security runs two chains: /api (stateless) and web (CSRF). JwtAuthenticationFilter reads the ACCESS_TOKEN cookie." "Servlet filters, Spring Security"
                    controllers = component "Controllers" "REST: /api/auth, /api/users, /api/posts, /api/feed -> JSON for the SPA. Web: /auth, /feed, /posts -> Thymeleaf HTML pages." "@RestController, @Controller"
                    errors = component "REST Exception Handler" "Turns domain exceptions into RFC 7807 ProblemDetail (404, 403, 409, 422)." "@RestControllerAdvice"
                }

                group "Application" {
                    authService = component "AuthService / UserService" "Register, login (BCrypt), issue JWT, look up users." "@Service"
                    postService = component "PostService" "Create / get / delete own post. Delegates feed listing." "@Service"
                    feedService = component "FeedService" "Chooses a ranking strategy by FeedSort (NEW, TRENDING)." "@Service, Strategy pattern"
                    interactionService = component "InteractionService" "Toggle like, add / list comments. Recomputes like and comment counters on the post." "@Service"
                }

                group "Domain" {
                    strategies = component "Feed Ranking Strategies" "Newest: latest posts. Trending: sorts recent posts by likes, comments, date (in memory)." "FeedRankingStrategy"
                    events = component "Domain Event Publisher" "Receives LikeCreatedEvent. Currently a no-op." "DomainEventPublisher (Null Object)"
                    repos = component "Repository Ports" "UserRepository, PostRepository, CommentRepository, LikeRepository + domain records." "Java interfaces"
                }

                group "Infrastructure" {
                    jwt = component "JwtTokenService" "Creates and validates HS256 JWTs." "jjwt"
                    adapters = component "JPA Adapters" "Implement the repository ports; map entities to domain records." "Spring Data JPA, Hibernate"
                }
            }

            database = container "Database" "Tables: users, posts, comments, likes. Schema created by Hibernate (ddl-auto: update)." "SQLite file instakill.db" "Database"
        }

        # ---- Context level
        user -> instakill "Uses the social feed" "Browser, HTTP"
        user -> imageHosts "Loads post images from" "HTTPS"

        # ---- Requests entering the backend (imply the container-level arrows)
        user -> spa "Uses" "Browser"
        spa -> filters "JSON requests to /api, ACCESS_TOKEN cookie" "HTTP/JSON"
        user -> filters "Opens server-rendered pages" "HTTP"

        filters -> controllers "Passes authenticated requests"
        filters -> jwt "Validates token with"
        controllers -> errors "REST exceptions mapped by"

        # ---- Presentation -> Application
        controllers -> authService "register, login, current user"
        controllers -> postService "create / get / delete post, list feed"
        controllers -> interactionService "toggle like, add / list comments"

        # ---- Application layer
        authService -> jwt "Issues token with"
        authService -> repos "Users"
        postService -> feedService "Lists feed via"
        postService -> repos "Posts"
        feedService -> strategies "Delegates ranking to"
        strategies -> repos "Reads latest posts"
        interactionService -> repos "Likes, comments, post counters"
        interactionService -> events "Publishes LikeCreatedEvent"

        # ---- Infrastructure
        repos -> adapters "Implemented by"
        adapters -> database "SQL queries" "JDBC, Hibernate"
    }

    views {
        systemContext instakill "Context" "Level 1 – System Context" {
            include *
            include imageHosts
            autoLayout lr
        }

        container instakill "Containers" "Level 2 – Containers" {
            include *
            autoLayout tb
        }

        component backend "Components" "Level 3 – Components of the Backend" {
            include *
            autoLayout lr
        }

        styles {
            element "Person" {
                shape Person
                background #08427b
                color #ffffff
            }
            element "Software System" {
                background #1168bd
                color #ffffff
            }
            element "External" {
                background #999999
                color #ffffff
            }
            element "Container" {
                background #438dd5
                color #ffffff
            }
            element "Component" {
                background #85bbf0
                color #000000
            }
            element "Database" {
                shape Cylinder
            }
            element "Browser" {
                shape WebBrowser
            }
        }
    }
}
