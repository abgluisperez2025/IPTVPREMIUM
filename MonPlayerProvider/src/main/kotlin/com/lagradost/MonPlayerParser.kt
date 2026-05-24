package com.anhdaden

import com.fasterxml.jackson.annotation.JsonProperty

data class Link(
    val name: String, 
    val type: String,
    val mainUrl: String, 
    val searchUrl: String? = null, 
)

data class LoadData(
    val url: String, 
    val title: String, 
    val poster: String, 
    val type: String, 
    val description: String?, 
)

data class LinkData(
    val id: String, 
    val url: String, 
)

data class ResponseHome(
    @JsonProperty("groups") val groups: ArrayList<Group>? = arrayListOf(),
    @JsonProperty("channels") val channels: ArrayList<Channel>? = arrayListOf(),
    @JsonProperty("search") val search: Search? = null,
) {
    data class Group(
        @JsonProperty("id") val id: String,
        @JsonProperty("name") val name: String,
        @JsonProperty("channels") val channels: ArrayList<Channel>? = arrayListOf(),
        @JsonProperty("url") val url: String? = null,
        @JsonProperty("remote_data") val remote_data: Share?,
    )

    data class Search(
        @JsonProperty("url") val url: String,
        @JsonProperty("suggest_url") val suggest_url: String?,
        @JsonProperty("search_key") val search_key: String,
        @JsonProperty("paging") val paging: Paging,
    )
}

data class ResponseHomeDetail(
    @JsonProperty("load_more") val loadMore: LoadMore,
    @JsonProperty("channels") val channels: ArrayList<Channel>? = arrayListOf(),
)

data class LoadMore(
    @JsonProperty("paging") val paging: Paging,
    @JsonProperty("pageInfo") val pageInfo: PageInfo,
)

data class Paging(
    @JsonProperty("page_key") val page_key: String,
    @JsonProperty("size_key") val size_key: String,
)

data class PageInfo(
    @JsonProperty("current_page") val current_page: Int,
    @JsonProperty("last_page") val last_page: Int,
    @JsonProperty("per_page") val per_page: Int,
    @JsonProperty("total") val total: Int,
)

data class Channel(
    @JsonProperty("id") val id: String,
    @JsonProperty("name") val name: String,
    @JsonProperty("description") val description: String?,
    @JsonProperty("image") val image: Image,
    @JsonProperty("type") val type: String,
    @JsonProperty("remote_data") val remote_data: Share?,
    @JsonProperty("share") val share: Share?,
    @JsonProperty("sources") val sources: ArrayList<Source>? = arrayListOf(),
    @JsonProperty("org_metadata") val org_metadata: Meta? = null,
)

data class Image(
    @JsonProperty("url") val url: String,
    @JsonProperty("height") val height: Int,
    @JsonProperty("width") val width: Int,
)

data class Share(
    @JsonProperty("url") val url: String,
    @JsonProperty("security") val security: Boolean?,
)

data class Meta(
    @JsonProperty("image") val image: String,
    @JsonProperty("title") val title: String,
    @JsonProperty("description") val description: String,
)

data class ResponseChanel(
    @JsonProperty("channel") val channel: ResponseDetail,
)

data class ResponseDetail(
    @JsonProperty("sources") val sources: ArrayList<Source>? = arrayListOf(),
)

data class Source(
    @JsonProperty("name") val name: String,
    @JsonProperty("contents") val contents: ArrayList<Content>? = arrayListOf(),
)

data class Content(
    @JsonProperty("id") val id: String,
    @JsonProperty("name") val name: String,
    @JsonProperty("streams") val streams: ArrayList<Stream>? = arrayListOf(),
)

data class Stream(
    @JsonProperty("id") val id: String,
    @JsonProperty("name") val name: String,
    @JsonProperty("remote_data") val remote_data: Share?,
    @JsonProperty("stream_links") val stream_links: ArrayList<StreamLink>? = arrayListOf(),
)

data class ResponseStream(
    @JsonProperty("stream_links") val stream_links: ArrayList<StreamLink>? = arrayListOf(),
)

data class StreamLink(
    @JsonProperty("id") val id: String,
    @JsonProperty("name") val name: String,
    @JsonProperty("type") val type: String,
    @JsonProperty("url") val url: String,
    @JsonProperty("request_headers") val request_headers: ArrayList<Header>? = arrayListOf(),
    @JsonProperty("subtitles") val subtitles: ArrayList<Subtitle>? = arrayListOf(),
)

data class Header(
    @JsonProperty("key") val key: String,
    @JsonProperty("value") val value: String,
)

data class Subtitle(
    @JsonProperty("url") val url: String,
    @JsonProperty("label") val label: String,
)
