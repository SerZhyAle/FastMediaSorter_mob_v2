# Architecture Overview

The application architecture is based on a multi-layered approach, grounded in the principles of **Clean Architecture** and the **MVVM (Model-View-ViewModel)** pattern. This choice is driven by the need to create a testable, scalable, and maintainable application, eliminating the logical inconsistencies present in the first version.

## 1. Core Principles

1.  **Separation of Concerns:** The application is divided into three main layers, each with its own clear area of responsibility:
    -   **UI (Presentation Layer):** Displays data and handles user input. Contains no business logic.
    -   **Domain (Business Logic Layer):** Contains the application's business logic (UseCases) and model definitions. This layer does not depend on UI implementation details or data sources.
    -   **Data (Data Layer):** Responsible for providing data from various sources (local DB, network, cloud).

2.  **Dependency Rule:** Dependencies are directed strictly inward: `UI` -> `Domain` -> `Data`. This means that business logic (`Domain`) knows nothing about the UI, and the data layer (`Data`) knows nothing about business logic. This is achieved through the use of interfaces (repositories) in the domain layer, which are implemented in the data layer.

3.  **Unidirectional Data Flow:** Interaction between components follows a predictable pattern:
    -   User event (button press) is passed from `View` (Activity/Fragment) to `ViewModel`.
    -   `ViewModel` calls the corresponding `UseCase` from the domain layer.
    -   `UseCase` queries the repository for data.
    -   Data is returned back through the chain and passed to `View` via reactive streams (`Kotlin Flow`), which `View` subscribes to.

## 2. Architecture Components

-   **View (Activity/Fragment):** A passive component responsible only for displaying data received from `ViewModel` and passing user events to it.
-   **ViewModel (Android Architecture Component):** Contains presentation logic. Prepares data for `View` and handles its events. Survives configuration changes (screen rotation). Interacts with `UseCases`.
-   **UseCase (Interactor):** Encapsulates a single specific business task (e.g., `GetMediaFilesUseCase`, `MoveFileUseCase`). Retrieves data from one or more repositories.
-   **Repository (Repository Pattern):** Abstraction over data sources. The `Domain` layer defines the repository interface, and the `Data` layer provides its implementation. The repository decides where to get data from — cache, local DB, or remote source.
-   **Dependency Injection (Hilt):** Hilt is used to provide dependencies (repositories, `UseCases`) to classes that need them (e.g., in `ViewModels`), eliminating the need to create objects manually.

## 3. Advantages of This Approach

-   **Testability:** Business logic in the `Domain` layer can be tested with simple Unit tests, as it does not depend on the Android Framework.
-   **Maintainability:** Clear structure simplifies code navigation, making changes, and fixing bugs.
-   **Scalability:** New features can be easily added by creating a new set of `View-ViewModel-UseCase`.
-   **Reliability:** Layer isolation minimizes the risk of cascading errors. Using `ViewModel` and `Flow` ensures correct operation with component lifecycles and asynchronous operations.