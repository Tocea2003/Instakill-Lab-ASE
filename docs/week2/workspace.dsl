workspace "Instakill" "C4 model of Instakill as it exists today (Week 2 lab)" {

    model {
        user = person "User" "Registers, logs in, reads the feed, creates posts, likes and comments. Anonymous visitors can only log in or register."

        imageHosts = softwareSystem "External Image Hosts" "Any public URL (e.g. Unsplash). Instakill stores only the image URL; the browser loads the image directly." "External"

        instakill = softwareSystem "Instakill" "Instagram-style social feed. One Spring Boot monolith + Vue SPA + SQLite." {

            spa = container "Vue SPA" "Single-page app: login/register, feed (Newest / Trending), composer, likes, comments." "Vue 3, Vite dev server :5173, axios" "Browser,Frontend"

            backend = container "Backend" "Serves the JSON REST API for the SPA and the server-rendered Thymeleaf pages. Single deployable." "Java 23, Spring Boot 3.3, port 8080" "Backend" {

                group "Presentation" {
                    filters = component "HTTP Filter Chain" "RequestIdFilter adds X-Request-Id. Spring Security runs two chains: /api (stateless) and web (CSRF). JwtAuthenticationFilter reads the ACCESS_TOKEN cookie." "Servlet filters, Spring Security" "Presentation"
                    controllers = component "Controllers" "REST: /api/auth, /api/users, /api/posts, /api/feed -> JSON for the SPA. Web: /auth, /feed, /posts -> Thymeleaf HTML pages." "@RestController, @Controller" "Presentation"
                    errors = component "REST Exception Handler" "Turns domain exceptions into RFC 7807 ProblemDetail (404, 403, 409, 422)." "@RestControllerAdvice" "Presentation"
                }

                group "Application" {
                    authService = component "AuthService / UserService" "Register, login (BCrypt), issue JWT, look up users." "@Service" "Application"
                    postService = component "PostService" "Create / get / delete own post. Delegates feed listing." "@Service" "Application"
                    feedService = component "FeedService" "Chooses a ranking strategy by FeedSort (NEW, TRENDING)." "@Service, Strategy pattern" "Application"
                    interactionService = component "InteractionService" "Toggle like, add / list comments. Recomputes like and comment counters on the post." "@Service" "Application"
                }

                group "Domain" {
                    strategies = component "Feed Ranking Strategies" "Newest: latest posts. Trending: sorts recent posts by likes, comments, date (in memory)." "FeedRankingStrategy" "Domain"
                    events = component "Domain Event Publisher" "Receives LikeCreatedEvent. Currently a no-op." "DomainEventPublisher (Null Object)" "Domain"
                    repos = component "Repository Ports" "UserRepository, PostRepository, CommentRepository, LikeRepository + domain records." "Java interfaces" "Domain"
                }

                group "Infrastructure" {
                    jwt = component "JwtTokenService" "Creates and validates HS256 JWTs." "jjwt" "Infrastructure"
                    adapters = component "JPA Adapters" "Implement the repository ports; map entities to domain records." "Spring Data JPA, Hibernate" "Infrastructure"
                }
            }

            database = container "Database" "Tables: users, posts, comments, likes. Schema created by Hibernate (ddl-auto: update)." "SQLite file instakill.db" "Database"
        }

        # ---- Context level
        user -> instakill "Uses the social feed" "Browser, HTTP" "FromUser"
        user -> imageHosts "Loads post images from" "HTTPS" "FromUser"

        # ---- Requests entering the backend (imply the container-level arrows)
        user -> spa "Uses" "Browser" "FromUser"
        spa -> filters "JSON requests to /api, ACCESS_TOKEN cookie" "HTTP/JSON" "FromFrontend"
        user -> filters "Opens server-rendered pages" "HTTP" "FromUser"

        filters -> controllers "Passes authenticated requests" "" "FromPresentation"
        filters -> jwt "Validates token with" "" "FromPresentation"
        controllers -> errors "REST exceptions mapped by" "" "FromPresentation"

        # ---- Presentation -> Application
        controllers -> authService "register, login, current user" "" "FromPresentation"
        controllers -> postService "create / get / delete post, list feed" "" "FromPresentation"
        controllers -> interactionService "toggle like, add / list comments" "" "FromPresentation"

        # ---- Application layer
        authService -> jwt "Issues token with" "" "FromApplication"
        authService -> repos "Users" "" "FromApplication"
        postService -> feedService "Lists feed via" "" "FromApplication"
        postService -> repos "Posts" "" "FromApplication"
        feedService -> strategies "Delegates ranking to" "" "FromApplication"
        strategies -> repos "Reads latest posts" "" "FromDomain"
        interactionService -> repos "Likes, comments, post counters" "" "FromApplication"
        interactionService -> events "Publishes LikeCreatedEvent" "" "FromApplication"

        # ---- Infrastructure
        repos -> adapters "Implemented by" "" "FromDomain"
        adapters -> database "SQL queries" "JDBC, Hibernate" "FromInfrastructure"
    }

    views {
        properties {
            "c4plantuml.tags" "true"
        }

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
                background #0b3d91
                color #ffffff
            }
            element "Software System" {
                background #1565c0
                color #ffffff
            }
            element "External" {
                background #8d8d8d
                color #ffffff
            }
            element "Container" {
                background #1e88e5
                color #ffffff
            }
            element "Frontend" {
                background #2e7d32
                color #ffffff
            }
            element "Backend" {
                background #1565c0
                color #ffffff
            }
            element "Database" {
                shape Cylinder
                background #d35400
                color #ffffff
            }
            element "Browser" {
                shape WebBrowser
            }
            element "Presentation" {
                background #00897b
                color #ffffff
            }
            element "Application" {
                background #f9a825
                color #000000
            }
            element "Domain" {
                background #7b1fa2
                color #ffffff
            }
            element "Infrastructure" {
                background #546e7a
                color #ffffff
            }
            relationship "Relationship" {
                dashed false
                thickness 2
                color #555555
            }
            relationship "FromUser" {
                color #0b3d91
            }
            relationship "FromFrontend" {
                color #2e7d32
            }
            relationship "FromPresentation" {
                color #00897b
            }
            relationship "FromApplication" {
                color #c17900
            }
            relationship "FromDomain" {
                color #7b1fa2
            }
            relationship "FromInfrastructure" {
                color #546e7a
            }
        }
    }
}
