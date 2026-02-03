<!-- <nav> -->
- [Akka](../index.html)
- [Tutorials](index.html)

<!-- </nav> -->

# Tutorials

Start with a guided tutorial or explore working samples. The resources below help you learn by building services and agents with Akka.

## <a href="about:blank#_tutorials"></a> Tutorials

These hands-on tutorials walk you step by step through building real Akka systems, from your first minimal agentic service to fully featured applications like shopping carts and AI agents. Each tutorial includes guided instructions, runnable code, and explanations of key concepts.

### <a href="about:blank#_build_your_first_service"></a> Build your first service

|  | **New to Akka? Start here.** |
A good first step for learning how services are structured and how Akka processes requests.

| Tutorial | Level |
| --- | --- |
| [Create and run your first agentic Hello World service](author-your-first-service.html) | Beginner |

### <a href="about:blank#_build_a_multi_agent_system"></a> Build a multi-agent system

Add agents and other components step-by-step. The final application will consist of dynamic orchestration of multiple agents. A workflow manages the user query process, handling the sequential steps of agent selection, plan creation, execution, and summarization.

| Tutorial | Level |
| --- | --- |
| [Part 1: Activity agent](planner-agent/activity.html) — An Agent (with session memory) that suggests real-world activities using an LLM. | Beginner |
| [Part 2: User preferences](planner-agent/preferences.html) — An Entity (long-term memory) to personalize the suggestions. | Beginner |
| [Part 3: Weather agent](planner-agent/weather.html) — A weather forecasting Agent that uses an external service as an agent tool. | Beginner |
| [Part 4: Orchestrate the agents](planner-agent/team.html) — A Workflow that coordinates long-running calls across the agents. | Intermediate |
| [Part 5: List by user](planner-agent/list.html) — A View that creates a read-only projection (i.e. a query) of all activity suggestions for a user. | Beginner |
| [Part 6: Dynamic orchestration](planner-agent/dynamic-team.html) — An Agent that creates a dynamic plan using an LLM, and a Workflow that executes the plan. | Advanced |
| [Part 7: Evaluation on changes](planner-agent/eval.html) — A Consumer that streams user preference changes to trigger an Agent. | Intermediate |

### <a href="about:blank#_build_a_shopping_cart_system"></a> Build a shopping cart system

Explore a complete e-commerce service and learn key Akka concepts by implementing a real-world system. These lessons walk you through defining agents, handling state, processing commands, and responding to user-specific queries.

| Tutorial | Level |
| --- | --- |
| [Part 1: Build a basic shopping cart with persistent state and command handling](shopping-cart/build-and-deploy-shopping-cart.html) | Beginner |
| [Part 2: Add user-specific lookup with JWT-based authentication](shopping-cart/addview.html) | Intermediate |

### <a href="about:blank#_build_an_ai_rag_agent"></a> Build an AI RAG Agent

Learn how to implement a Retrieval-Augmented Generation (RAG) pipeline with Akka. This series covers end-to-end design of a multi-agent system that performs LLM-assisted reasoning, indexing, and live querying.

| Tutorial | Level |
| --- | --- |
| [Part 1: The agent](ask-akka-agent/the-agent.html) | Beginner |
| [Part 2: Build a workflow that indexes knowledge using semantic embeddings](ask-akka-agent/indexer.html) | Intermediate |
| [Part 3: Executing RAG queries](ask-akka-agent/rag.html) | Intermediate |
| [Part 4: Adding UI endpoints](ask-akka-agent/endpoints.html) | Advanced |

## <a href="about:blank#_explore_sample_applications"></a> Explore sample applications

These runnable [code samples](samples.html) showcase common patterns and advanced architectures built with Akka. They are designed for exploration and reference rather than step-by-step instruction.

<!-- <footer> -->
<!-- <nav> -->
[Getting started](starthere.html) [Hello world agent](author-your-first-service.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->