# Orco #

Simple web service written in Scala using ZIO that given the name of the organization will return a list of contributors sorted by the number of contributions. 

### API definition ###

    GET /org/{org_name}/contributors

    {
      "name": "Szczyp",
      "contributions": 1,
    }

## Things to improve ##
  * HttpClient resilience (ZIO Schedule)
  * HttpClient throttling depending on Github API's rate limits (X-RateLimit-*)
  * Service rate limits
  * Response caching (ETag)
