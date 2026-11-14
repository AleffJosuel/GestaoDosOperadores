package br.com.gestao.operadores.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.time.Duration;

/**
 * Serve o site React que o Maven embute no JAR (em {@code classpath:/static}), para que um único
 * arquivo entregue interface e API na mesma porta — sem Node, nginx ou IIS no servidor da empresa.
 *
 * <p>Resolve o problema clássico de SPA em servidor: o React Router inventa caminhos que não são
 * arquivos ({@code /mapa}, {@code /ficha/12}). Quem digita um deles na barra de endereço, ou
 * recarrega a página estando nele, faz o navegador pedir aquele caminho ao servidor — que não tem
 * arquivo nenhum ali e responderia 404. O resolvedor abaixo devolve o {@code index.html} nesses
 * casos e deixa o roteamento para o front.
 */
@Configuration
public class SiteConfig implements WebMvcConfigurer {

    private static final String RAIZ = "classpath:/static/";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registro) {
        // Assets com hash no nome (index-CMv1vZ__.js): o conteúdo nunca muda sem o nome mudar,
        // então podem ficar no cache do navegador por um ano.
        registro.addResourceHandler("/assets/**")
            .addResourceLocations(RAIZ + "assets/")
            .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());

        // index.html e as rotas do SPA: sem cache. Um index.html cacheado continuaria pedindo
        // assets da versão anterior depois de uma atualização do sistema, e a tela abriria em branco.
        registro.addResourceHandler("/**")
            .addResourceLocations(RAIZ)
            .setCacheControl(CacheControl.noStore())
            .resourceChain(true)
            .addResolver(new ResolvedorDeRotasDoSpa());
    }

    /**
     * Arquivo existente é servido como sempre; caminho sem arquivo cai no {@code index.html} —
     * exceto nos dois casos em que 404 é a resposta certa (ver abaixo).
     */
    static class ResolvedorDeRotasDoSpa extends PathResourceResolver {

        @Override
        protected Resource getResource(String caminho, Resource local) throws IOException {
            // super faz a busca normal e, de brinde, a checagem de que o caminho resolvido está
            // mesmo dentro de /static (proteção contra ../ na URL). Não dá para pular.
            Resource encontrado = super.getResource(caminho, local);
            if (encontrado != null) {
                return encontrado;
            }
            // Chamada de API inexistente: 404 em JSON, pelo ErrosHandler. Devolver HTML aqui faria
            // o cliente receber 200 com uma página onde esperava dados.
            if (caminho.startsWith("api/")) {
                return null;
            }
            // Caminho com extensão é pedido de arquivo (script, imagem, favicon): se não existe, é
            // 404 de verdade. Sem esta linha, um asset faltando "carregaria" o index.html e a
            // falha apareceria como erro de sintaxe, longe da causa.
            if (temExtensao(caminho)) {
                return null;
            }
            // Resolve a partir da própria localização, e não de um caminho fixo: assim vale tanto
            // para o /static de dentro do JAR quanto para uma pasta no disco, e um JAR empacotado
            // sem o front responde 404 honesto em vez de estourar procurando o index.
            Resource index = local.createRelative("index.html");
            return index.exists() ? index : null;
        }

        /** Extensão no último segmento do caminho (ex.: {@code assets/app.js}, mas não {@code ficha/12}). */
        private static boolean temExtensao(String caminho) {
            int ultimaBarra = caminho.lastIndexOf('/');
            return caminho.indexOf('.', ultimaBarra + 1) >= 0;
        }
    }
}
