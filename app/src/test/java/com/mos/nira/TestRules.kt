package com.mos.nira

import com.mos.nira.features.artwork.data.MeshDepositRule
import com.mos.nira.features.artwork.data.PrintRulesRepository
import com.mos.nira.features.artwork.data.ScreenMeshRules
import org.json.JSONObject
import java.io.File

/** The real print_rules.json for tests, so no test keeps its own copy of rule numbers. */
object TestRules {
    val root: JSONObject by lazy {
        val path = "src/main/assets/print_rules.json"
        JSONObject(listOf(File(path), File("app/$path")).first { it.exists() }.readText())
    }
    val deposits: Map<Int, MeshDepositRule> by lazy { PrintRulesRepository.parseMeshDepositRules(root) }
    val meshRules: ScreenMeshRules by lazy { PrintRulesRepository.parseScreenMeshRules(root)!! }
}
