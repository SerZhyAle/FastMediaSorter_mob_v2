package com.sza.fastmediasorter.domain.model.launcher

/** S4563: another category already shows this name; the catalog was left as it was. */
class AppCategoryNameTakenException(name: String) :
    IllegalArgumentException("A category named \"$name\" already exists")
