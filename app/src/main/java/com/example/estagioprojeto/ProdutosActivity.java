package com.example.estagioprojeto;

import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.webkit.WebChromeClient;
import android.webkit.JavascriptInterface;
import android.database.sqlite.SQLiteDatabase;
import androidx.appcompat.app.AppCompatActivity;
import android.database.Cursor;
import android.util.Log;
import android.webkit.ConsoleMessage;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

public class ProdutosActivity extends AppCompatActivity {

    private WebView webView;
    private Bancodedados dbHelper;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_produtos);

        dbHelper = new Bancodedados(this);

        webView = findViewById(R.id.webViewProdutos);
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);

        // Captura logs do JS no Logcat
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                Log.d("JS_LOG", consoleMessage.message() + " -- linha: " + consoleMessage.lineNumber());
                return true;
            }
        });

        // Gera um novo id_pedido para esta "sessão de pedidos"
        final int novoIdPedido = dbHelper.gerarIdPedido();

        // Interface para o JS acessar os produtos
        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public String getProdutosCartazista() {
                List<Produto> produtos = dbHelper.listarProdutosCartazistaComoLista();
                JSONArray jsonArray = new JSONArray();

                for (Produto p : produtos) {
                    try {
                        JSONObject obj = new JSONObject();
                        obj.put("codigo", p.getCodigo());
                        obj.put("descricao", p.getDescricao());
                        obj.put("categoria", p.getCategoria());
                        obj.put("preco", p.getPreco());
                        obj.put("embalagem", p.getEmbalagem());
                        obj.put("qtd_por_embalagem", p.getQtdPorEmbalagem());
                        jsonArray.put(obj);
                    } catch (Exception e) {
                        Log.e("ProdutosActivity", "Erro ao criar JSON do produto", e);
                    }
                }

                Log.d("ProdutosActivity", "JSON final de produtos: " + jsonArray.toString());
                return jsonArray.toString();
            }

            @JavascriptInterface
            public void finalizarPedido(int codigo, int quantidade, double preco) {
                dbHelper.inserirPedido(novoIdPedido, codigo, quantidade, preco);
                Log.d("ProdutosActivity", "Pedido inserido: " + codigo + " x" + quantidade + " (id_pedido: " + novoIdPedido + ")");
            }

            @JavascriptInterface
            public String getPedidosDaSemana() {
                return ProdutosActivity.this.getPedidosDaSemana();
            }

            @JavascriptInterface
            public String getPedidosDoMes() {
                return ProdutosActivity.this.getPedidosDoMes();
            }
        }, "Android");

        webView.loadUrl("file:///android_asset/produtos.html");
    }



    @JavascriptInterface
    public String getPedidosDaSemana() {
        JSONArray array = new JSONArray();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        try {
            String sql = "SELECT p.id, p.id_pedido, p.codigo_produto, pr.descricao, pr.embalagem, pr.qtd_por_embalagem, " +
                    "p.quantidade, p.preco, p.data_pedido " +
                    "FROM pedidos p " +
                    "INNER JOIN produtos_cartazista pr ON p.codigo_produto = pr.codigo " +
                    "WHERE date(p.data_pedido) >= date('now','-6 days') " +
                    "ORDER BY p.data_pedido ASC";

            Cursor cursor = db.rawQuery(sql, null);

            if(cursor.moveToFirst()) {
                do {
                    try {
                        JSONObject obj = new JSONObject();
                        obj.put("id", cursor.getInt(cursor.getColumnIndexOrThrow("id")));
                        obj.put("id_pedido", cursor.getInt(cursor.getColumnIndexOrThrow("id_pedido"))); // <-- adicionado
                        obj.put("codigo_produto", cursor.getInt(cursor.getColumnIndexOrThrow("codigo_produto")));
                        obj.put("descricao", cursor.getString(cursor.getColumnIndexOrThrow("descricao")));
                        obj.put("embalagem", cursor.getString(cursor.getColumnIndexOrThrow("embalagem")));
                        obj.put("qtd_por_embalagem", cursor.getDouble(cursor.getColumnIndexOrThrow("qtd_por_embalagem")));
                        obj.put("quantidade", cursor.getInt(cursor.getColumnIndexOrThrow("quantidade")));
                        obj.put("preco", cursor.getDouble(cursor.getColumnIndexOrThrow("preco")));
                        obj.put("data_pedido", cursor.getString(cursor.getColumnIndexOrThrow("data_pedido")));
                        array.put(obj);
                    } catch (Exception e) {
                        Log.e("getPedidosDaSemana", "Erro ao processar registro do cursor", e);
                    }
                } while(cursor.moveToNext());
            }
            if (cursor != null) cursor.close();
        } catch (Exception e) {
            Log.e("getPedidosDaSemana", "Erro na query SQL", e);
        }

        return array.toString();
    }


    @JavascriptInterface
    public String getPedidosDoMes() {
        JSONArray array = new JSONArray();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String sql = "SELECT strftime('%W', p.data_pedido) AS semana, pr.descricao, " +
                "SUM(p.quantidade) AS quantidade_total, SUM(p.preco * p.quantidade) AS valor_total " +
                "FROM pedidos p " +
                "INNER JOIN produtos_cartazista pr ON p.codigo_produto = pr.codigo " +
                "WHERE strftime('%m', p.data_pedido) = strftime('%m','now') " +
                "GROUP BY semana, pr.descricao " +
                "ORDER BY semana ASC";

        Cursor cursor = db.rawQuery(sql, null);

        if(cursor.moveToFirst()) {
            do {
                try {
                    JSONObject obj = new JSONObject();
                    obj.put("semana", cursor.getString(cursor.getColumnIndexOrThrow("semana")));
                    obj.put("descricao", cursor.getString(cursor.getColumnIndexOrThrow("descricao")));
                    obj.put("quantidade_total", cursor.getInt(cursor.getColumnIndexOrThrow("quantidade_total")));
                    obj.put("valor_total", cursor.getDouble(cursor.getColumnIndexOrThrow("valor_total")));
                    array.put(obj);
                } catch (Exception e) { e.printStackTrace(); }
            } while(cursor.moveToNext());
        }

        cursor.close();
        return array.toString();
    }
}
