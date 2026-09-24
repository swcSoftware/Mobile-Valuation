package com.swcsoftware.valuelens.ui

import com.swcsoftware.valuelens.core.CompanyNames
import com.swcsoftware.valuelens.domain.CompanyRef

/**
 * How the name is *shown* — "Merck & Co., Inc.", never "MERCK & CO., INC." (ISSUES #85). The rule
 * is the core's, so iOS and Android agree. `name` stays the filed name: use it wherever the name is
 * evidence or a lookup key.
 */
val CompanyRef.displayName: String get() = CompanyNames.display(name, ticker)
