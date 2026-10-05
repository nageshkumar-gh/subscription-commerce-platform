{{- define "service.labels" -}}
app.kubernetes.io/name: {{ .Values.name }}
app.kubernetes.io/part-of: subscription-platform
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{- define "service.mongoLabels" -}}
app.kubernetes.io/name: {{ .Values.mongodb.name }}
app.kubernetes.io/part-of: subscription-platform
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}
