package ru.neosvet.vestnewage.view.browser

import android.webkit.JavascriptInterface
import android.webkit.WebView
import okhttp3.FormBody
import okhttp3.RequestBody
import org.intellij.lang.annotations.Language
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.Charset
import java.util.Locale

//based on https://github.com/acsbendi/Android-Request-Inspector-WebView

class RequestInspector {
    private val recordedRequests = ArrayList<RecordedRequest>()

    fun findRecordedRequestForUrl(url: String): RecordedRequest? {
        return synchronized(recordedRequests) {
            recordedRequests.findLast { recordedRequest ->
                url == recordedRequest.url
            } ?: recordedRequests.findLast { recordedRequest ->
                url.contains(recordedRequest.url)
            }
        }
    }

    data class RecordedRequest(
        val url: String,
        val method: String,
        val body: RequestBody,
        val formParameters: Map<String, String>,
        val headers: Map<String, String>,
        val trace: String
    )

    @JavascriptInterface
    fun recordFormSubmission(
        url: String,
        method: String,
        formParameterList: String,
        headers: String,
        trace: String
    ) {
        val formParameterJsonArray = JSONArray(formParameterList)
        val headerMap = getHeadersAsMap(headers)
        val formParameterMap = getFormParametersAsMap(formParameterJsonArray)

        headerMap["content-type"] = "application/x-www-form-urlencoded"
        val body = getFormBody(formParameterJsonArray)

        addRecordedRequest(
            RecordedRequest(
                url,
                method,
                body,
                formParameterMap,
                headerMap,
                trace
            )
        )
    }

    private fun addRecordedRequest(recordedRequest: RecordedRequest) {
        synchronized(recordedRequests) {
            recordedRequests.add(recordedRequest)
        }
    }

    private fun getHeadersAsMap(headersString: String): MutableMap<String, String> {
        val headersObject = JSONObject(headersString)
        val map = HashMap<String, String>()
        for (key in headersObject.keys()) {
            val lowercaseHeader = key.lowercase(Locale.getDefault())
            map[lowercaseHeader] = headersObject.getString(key)
        }
        return map
    }

    private fun getFormParametersAsMap(formParameterJsonArray: JSONArray): Map<String, String> {
        val map = HashMap<String, String>()
        repeat(formParameterJsonArray.length()) { i ->
            val formParameter = formParameterJsonArray.get(i) as JSONObject
            val name = formParameter.getString("name")
            val value = formParameter.optString("value")
            val checked = formParameter.optBoolean("checked")
            val type = formParameter.optString("type")
            if (!isExcludedFormParameter(type, checked)) {
                map[name] = value
            }
        }
        return map
    }

    private fun getFormBody(formParameterJsonArray: JSONArray): RequestBody {
        val builder = FormBody.Builder(Charset.forName("cp1251"))
        repeat(formParameterJsonArray.length()) { i ->
            val formParameter = formParameterJsonArray.get(i) as JSONObject
            val name = formParameter.getString("name")
            val value = formParameter.optString("value")
            val checked = formParameter.optBoolean("checked")
            val type = formParameter.optString("type")

            if (name.isNotEmpty() && !isExcludedFormParameter(type, checked)) {
                builder.add(name, value)
            }
        }
        return builder.build()
    }

    private fun isExcludedFormParameter(type: String, checked: Boolean): Boolean {
        return (type == "radio" || type == "checkbox") && !checked
    }

    companion object {
        const val INTERFACE_NAME = "RequestInspection"

        @Language("JS")
        private const val JAVASCRIPT_INTERCEPTION_CODE = """
function getFullUrl(url) {
    if (url.startsWith("/")) {
        return location.protocol + '//' + location.host + url;
    } else {
        return url;
    }
}

function recordFormSubmission(form) {
    var jsonArr = [];
    for (i = 0; i < form.elements.length; i++) {
        var parName = form.elements[i].name;
        var parValue = form.elements[i].value;
        var parType = form.elements[i].type;
        var parChecked = form.elements[i].checked;
        var parId = form.elements[i].id;

        jsonArr.push({
            name: parName,
            value: parValue,
            type: parType,
            checked:parChecked,
            id:parId
        });
    }

    const path = form.attributes['action'] === undefined ? "/" : form.attributes['action'].nodeValue;
    const method = form.attributes['method'] === undefined ? "GET" : form.attributes['method'].nodeValue;
    const url = getFullUrl(path);
    const err = new Error();
    $INTERFACE_NAME.recordFormSubmission(
        url,
        method,
        JSON.stringify(jsonArr),
        "{}",
        err.stack
    );
}

function handleFormSubmission(e) {
    const form = e ? e.target : this;
    recordFormSubmission(form);
    form._submit();
}

HTMLFormElement.prototype._submit = HTMLFormElement.prototype.submit;
HTMLFormElement.prototype.submit = handleFormSubmission;
window.addEventListener('submit', function (submitEvent) {
    const form = submitEvent ? submitEvent.target : this;
    recordFormSubmission(form);
}, true);
        """

        fun enabledRequestInspection(webView: WebView) {
            webView.evaluateJavascript(
                "javascript: $JAVASCRIPT_INTERCEPTION_CODE",
                null
            )
        }
    }
}
