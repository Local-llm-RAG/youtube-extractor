package com.data.oai.zenodo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.ToString;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@ToString
@Data

public class ZenodoRecord {

    // root timestamps
    private String created;
    private String modified;

    // ids
    private Long id;

    @JsonProperty("recid")
    private String recid;

    @JsonProperty("conceptrecid")
    private String conceptRecId;

    // dois
    private String doi;

    @JsonProperty("conceptdoi")
    private String conceptDoi;

    @JsonProperty("doi_url")
    private String doiUrl;

    // title duplicated at root and metadata
    private String title;

    private Links links;

    // root files
    private List<FileEntry> files;

    // root misc
    private String updated;
    private Integer revision;
    private String status;
    private String state;
    private Boolean submitted;

    private Stats stats;

    private Metadata metadata;

    // ---- nested types ----

    @JsonIgnoreProperties(ignoreUnknown = true)
    @ToString
    @Data
    public static class Links {
        private String self;

        @JsonProperty("self_html")
        private String selfHtml;

        @JsonProperty("preview_html")
        private String previewHtml;

        private String doi;

        @JsonProperty("self_doi")
        private String selfDoi;

        @JsonProperty("self_doi_html")
        private String selfDoiHtml;

        @JsonProperty("reserve_doi")
        private String reserveDoi;

        private String parent;

        @JsonProperty("parent_html")
        private String parentHtml;

        @JsonProperty("parent_doi")
        private String parentDoi;

        @JsonProperty("parent_doi_html")
        private String parentDoiHtml;

        @JsonProperty("self_iiif_manifest")
        private String selfIiifManifest;

        @JsonProperty("self_iiif_sequence")
        private String selfIiifSequence;

        private String files;

        @JsonProperty("media_files")
        private String mediaFiles;

        private String archive;

        @JsonProperty("archive_media")
        private String archiveMedia;

        private String latest;

        @JsonProperty("latest_html")
        private String latestHtml;

        private String versions;
        private String draft;

        @JsonProperty("access_links")
        private String accessLinks;

        @JsonProperty("access_grants")
        private String accessGrants;

        @JsonProperty("access_users")
        private String accessUsers;

        @JsonProperty("access_request")
        private String accessRequest;

        private String access;

        private String communities;

        @JsonProperty("communities-suggestions")
        private String communitiesSuggestions;

        @JsonProperty("request_deletion")
        private String requestDeletion;

        @JsonProperty("file_modification")
        private String fileModification;

        private String requests;

        /**
         * Thumbnails object has numeric-string keys: "10","50","100","250","750","1200"
         */
        private Map<String, String> thumbnails;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @ToString
    @Data
    public static class FileEntry {
        private String id;
        private String key;
        private Long size;
        private String checksum;
        private FileLinks links;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @ToString
    @Data
    public static class FileLinks {
        private String self;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @ToString
    @Data
    public static class Metadata {
        private String title;
        private String doi;

        @JsonProperty("privateation_date")
        private String privateationDate;

        private String description;

        @JsonProperty("access_right")
        private String accessRight;

        private List<Creator> creators;

        private List<String> keywords;

        private List<DateEntry> dates;

        private String language;

        /**
         * Custom is highly variable. Keep it as Map.
         */
        private Map<String, Object> custom;

        @JsonProperty("resource_type")
        private ResourceType resourceType;

        @JsonProperty("alternate_identifiers")
        private List<AlternateIdentifier> alternateIdentifiers;

        private License license;

        private Relations relations;

        private Journal journal;

        /**
         * Sometimes present in metadata as well:
         */
        private List<CommunityRef> communities;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @ToString
    @Data
    public static class Creator {
        private String name;
        private String affiliation;
        private String orcid;

        @JsonProperty("given_name")
        private String givenName;

        @JsonProperty("family_name")
        private String familyName;

        private String type;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @ToString
    @Data
    public static class DateEntry {
        private String type;
        private String description;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @ToString
    @Data
    public static class ResourceType {
        private String title;
        private String type;
        private String subtype;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @ToString
    @Data
    public static class AlternateIdentifier {
        private String identifier;
        private String scheme;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @ToString
    @Data
    public static class License {
        private String id;
        private String title;
        private String url;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @ToString
    @Data
    public static class Relations {
        private List<VersionRelation> version;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @ToString
    @Data
    public static class VersionRelation {
        private Integer index;

        @JsonProperty("is_last")
        private Boolean isLast;

        private ParentPid parent;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @ToString
    @Data
    public static class ParentPid {
        @JsonProperty("pid_type")
        private String pidType;

        @JsonProperty("pid_value")
        private String pidValue;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @ToString
    @Data
    public static class Journal {
        private String issue;
        private String title;
        private String volume;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @ToString
    @Data
    public static class CommunityRef {
        private String id;

        // sometimes other fields exist
        private String title;
        private String identifier;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @ToString
    @Data
    public static class Stats {
        private Integer downloads;

        @JsonProperty("unique_downloads")
        private Integer uniqueDownloads;

        private Integer views;

        @JsonProperty("unique_views")
        private Integer uniqueViews;

        @JsonProperty("version_downloads")
        private Integer versionDownloads;

        @JsonProperty("version_unique_downloads")
        private Integer versionUniqueDownloads;

        @JsonProperty("version_unique_views")
        private Integer versionUniqueViews;

        @JsonProperty("version_views")
        private Integer versionViews;
    }
}
