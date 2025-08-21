package com.example.estagioprojeto;

import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.database.Cursor;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private Bancodedados dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbHelper = new Bancodedados(this);

        webView = findViewById(R.id.webview);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);

        // Liga a interface JS -> Android para comunicação
        webView.addJavascriptInterface(new WebAppInterface(), "Android");
        carregarPaginaFaixas();
    }

    private void carregarPaginaFaixas() {
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                enviarFaixasParaJS();
            }
        });
        webView.setWebChromeClient(new android.webkit.WebChromeClient());
        webView.loadUrl("file:///android_asset/login.html");
    }

    private void enviarFaixasParaJS() {
        Cursor cursor = dbHelper.listarFaixas();
        JSONArray faixasArray = new JSONArray();

        if (cursor.moveToFirst()) {
            do {
                try {
                    JSONObject faixaObj = new JSONObject();
                    faixaObj.put("id", cursor.getInt(cursor.getColumnIndex("id")));
                    faixaObj.put("nome_produto", cursor.getString(cursor.getColumnIndex("produto")));
                    faixaObj.put("tipo_oferta", cursor.getString(cursor.getColumnIndex("tipo_oferta")));
                    faixaObj.put("preco_oferta", cursor.isNull(cursor.getColumnIndex("preco_oferta")) ? JSONObject.NULL : cursor.getDouble(cursor.getColumnIndex("preco_oferta")));
                    faixaObj.put("preco_normal", cursor.isNull(cursor.getColumnIndex("preco_normal")) ? JSONObject.NULL : cursor.getDouble(cursor.getColumnIndex("preco_normal")));
                    faixaObj.put("estado_faixa", cursor.getString(cursor.getColumnIndex("estado")));
                    faixaObj.put("condicao_faixa", cursor.getString(cursor.getColumnIndex("condicao")));
                    faixaObj.put("comentario", cursor.getString(cursor.getColumnIndex("comentario")));
                    faixaObj.put("limite_cpf", cursor.getInt(cursor.getColumnIndex("limite_cpf")));
                    faixaObj.put("vezes_usada", cursor.getInt(cursor.getColumnIndex("vezes_usada")));

                    String dataCriacao = cursor.getString(cursor.getColumnIndex("data_criacao"));
                    faixaObj.put("tempo_faixa", calcularTempoFaixa(dataCriacao));

                    faixasArray.put(faixaObj);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } while (cursor.moveToNext());
        }
        cursor.close();

        String jsCommand = "javascript:carregarFaixas(" + faixasArray.toString() + ")";
        webView.evaluateJavascript(jsCommand, null);
    }

    private String calcularTempoFaixa(String dataCriacao) {
        if (dataCriacao == null) return "0 dias";

        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
            java.util.Date inicio = sdf.parse(dataCriacao);
            java.util.Date hoje = new java.util.Date();

            long diff = hoje.getTime() - inicio.getTime(); // diferença em ms
            long dias = diff / (1000L * 60 * 60 * 24);

            long anos = dias / 360;
            dias %= 360;

            long meses = dias / 30;
            dias %= 30;

            long semanas = dias / 7;
            dias %= 7;

            StringBuilder sb = new StringBuilder();
            if (anos > 0) sb.append(anos).append(anos == 1 ? " ano " : " anos ");
            if (meses > 0) sb.append(meses).append(meses == 1 ? " mês " : " meses ");
            if (semanas > 0) sb.append(semanas).append(semanas == 1 ? " semana " : " semanas ");
            if (dias > 0) sb.append(dias).append(dias == 1 ? " dia" : " dias");

            String resultado = sb.toString().trim();

            // resumir se ficar muito grande (mais de 2 unidades)
            String[] partes = resultado.split(" ");
            if (partes.length > 4) {
                resultado = partes[0] + " " + partes[1] + " e " + partes[2] + " " + partes[3];
            }

            return resultado.isEmpty() ? "0 dias" : resultado;

        } catch (Exception e) {
            return "0 dias";
        }
    }

    private class WebAppInterface {
        @android.webkit.JavascriptInterface
        public void excluirFaixa(int id) {
            runOnUiThread(() -> {
                boolean sucesso = dbHelper.deletarFaixa(id);
                if (sucesso) {
                    Toast.makeText(MainActivity.this, "Faixa excluída!", Toast.LENGTH_SHORT).show();
                    enviarFaixasParaJS();
                } else {
                    Toast.makeText(MainActivity.this, "Erro ao excluir faixa.", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @android.webkit.JavascriptInterface
        public void editarFaixa(int id, String produto, String tipoOferta,
                                double precoOferta, double precoNormal,
                                String estado, String condicao,
                                String comentario, int limiteCpf) {

            runOnUiThread(() -> {
                boolean sucesso = dbHelper.editarFaixa(id, produto, tipoOferta,
                        precoOferta, precoNormal, estado, condicao, comentario, limiteCpf);

                if (sucesso) {
                    Toast.makeText(MainActivity.this, "Faixa atualizada!", Toast.LENGTH_SHORT).show();
                    enviarFaixasParaJS();
                } else {
                    Toast.makeText(MainActivity.this, "Erro ao atualizar faixa.", Toast.LENGTH_SHORT).show();
                }
            });
        }


        @android.webkit.JavascriptInterface
        public void usarFaixa(int id, int dias) {
            runOnUiThread(() -> {
                Log.d("DEBUG_USAR_FAIXA", "Chamando usarFaixa com id=" + id + ", dias=" + dias);

                try {
                    if (dias <= 0) {
                        Log.e("DEBUG_USAR_FAIXA", "Número de dias inválido: " + dias);
                        Toast.makeText(MainActivity.this, "Número de dias inválido!", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    boolean sucesso = dbHelper.iniciarUso(id, dias);
                    if (sucesso) {
                        Log.d("DEBUG_USAR_FAIXA", "Faixa iniciada com sucesso: id=" + id);
                        Toast.makeText(MainActivity.this, "Faixa em uso por " + dias + " dias!", Toast.LENGTH_SHORT).show();
                    } else {
                        Log.e("DEBUG_USAR_FAIXA", "Erro ao iniciar uso, faixa não encontrada: id=" + id);
                        Toast.makeText(MainActivity.this, "Erro: faixa não encontrada.", Toast.LENGTH_SHORT).show();
                    }
                    enviarFaixasParaJS();
                } catch (Exception e) {
                    Log.e("DEBUG_USAR_FAIXA", "Exception ao usar faixa", e);
                    Toast.makeText(MainActivity.this, "Erro inesperado ao usar faixa.", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @android.webkit.JavascriptInterface
        public void cancelarUsoFaixa(int id) {
            runOnUiThread(() -> {
                boolean sucesso = dbHelper.cancelarUso(id);
                if (sucesso) {
                    Toast.makeText(MainActivity.this, "Uso da faixa cancelado!", Toast.LENGTH_SHORT).show();
                    enviarFaixasParaJS();
                } else {
                    Toast.makeText(MainActivity.this, "Erro ao cancelar uso.", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
}
