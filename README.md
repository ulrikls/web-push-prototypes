# Asynchronous Server-to-client Communication

Architectural prototypes of three protocols for asynchronous server-to-client communication for Web.

The protocols are:
* [HTTP long polling](https://en.wikipedia.org/wiki/Push_technology#Long_polling)
* [Server-sent events](https://html.spec.whatwg.org/multipage/server-sent-events.html)
* [WebSockets](https://websockets.spec.whatwg.org/)

The server is implemented using [Javalin](https://javalin.io/).


## Building

`./gradlew build`


## Running

To run the server with default arguments:<br>
`./gradlew run`

The server listens to [localhost:7070](http://localhost:7070/)

To provide arguments for the server, use:<br>
`java -jar app/build/libs/app.jar <payload size> <message interval>`

The arguments are:
* **Payload size**:
  Size of the random payload string, in bytes.
  Default is 0 bytes.
* **Message interval**:
  Mean time interval between messages, in milliseconds.
  The interval follows a Gaussian distribution with a standard deviation of 100 ms.
  Default is 1000 ms.


## Output

Latency records are stored in a CSV file named
`LogReply-<server startup time>-<payload size>.csv`

The CSV file contains four columns:
1. ISO-8601 timestamp of when the message was sent.
2. Google Sheets compatible message timestamp.
3. Protocol. Possible values are:
   * **LP** = HTTP long polling
   * **SSE** = Server-sent events
   * **WS** = WebSockets
4. Round-trip latency, in nanoseconds.

The CSV file is updated every 30 seconds while the server is running.
