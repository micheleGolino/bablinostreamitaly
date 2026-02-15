// use an integer for version numbers
version = 1


cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "Live sports streams from Hattrick Sport"
    authors = listOf("bablino")

    /**
    * Status int as the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta only
    * */
    status = 1

    tvTypes = listOf("Live")

    requiresResources = false
    language = "it"

    iconUrl = "https://raw.githubusercontent.com/michelegolino/bablinostreamitaly/refs/heads/master/Hattrick/hattrick.png"
}
