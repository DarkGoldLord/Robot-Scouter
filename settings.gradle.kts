include(
        ":app:android-base", "app:server",

        ":library:common",
        ":library:core", ":library:core-model", ":library:core-data", ":library:core-ui",
        ":library:shared", ":library:shared-scouting",

        ":feature:teams", ":feature:autoscout",
        ":feature:scouts", ":feature:templates", ":feature:settings",
        ":feature:exports"
)
