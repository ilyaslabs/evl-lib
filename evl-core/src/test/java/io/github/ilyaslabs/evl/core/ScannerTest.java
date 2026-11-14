package io.github.ilyaslabs.evl.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScannerTest {

    private static FlowNode find(FlowModel m, String path) {
        List<FlowNode> all = new ArrayList<>();
        m.walk(all::add);
        return all.stream().filter(n -> n.path.equals(path)).findFirst().orElseThrow();
    }

    private static List<String> children(FlowNode n) {
        return n.children.stream().map(FlowNode::segment).toList();
    }

    @Test
    void mediaExample() {
        FlowModel m = Scanner.scan(Path.of(System.getProperty("evl.examples"), "media"));
        assertThat(m.problems).isEmpty();
        FlowNode upload = find(m, "image-upload");
        assertThat(upload.kind).isEqualTo("endpoint");
        assertThat(upload.subtitle).isEqualTo("POST /media");
        assertThat(upload.caller).isEqualTo("Mobile app");
        assertThat(children(upload)).containsExactly("validate", "db", "s3", "queue", "thumbnail");
        assertThat(find(m, "image-upload>thumbnail").async).isTrue();
        assertThat(children(find(m, "image-upload>validate"))).containsExactly("size", "type", "quota");
        assertThat(find(m, "image-upload>validate>quota").uses).containsExactly("database:postgres");
        assertThat(find(m, "image-upload>db").resource).isEqualTo("database:postgres");
        assertThat(children(find(m, "image-upload>s3"))).containsExactly("key", "presign", "cache");
        assertThat(children(find(m, "image-upload>thumbnail"))).containsExactly("download", "resize", "upload", "ready");
        assertThat(upload.allUses()).contains("database:postgres", "storage:Amazon S3", "queue:media.uploaded");
        assertThat(m.resources).extracting(r -> r.id).contains("database:postgres", "storage:Amazon S3");
        assertThat(upload.code.lines()).hasSizeLessThanOrEqualTo(21);
    }

    @Test
    void kotlinSources() {
        String api = """
                package shop

                import io.github.ilyaslabs.evl.EVL
                import io.github.ilyaslabs.evl.Flow

                @EVL.Endpoint(path = "/orders")
                class OrderController(private val service: OrderService) {
                    @EVL.Endpoint(method = "POST", caller = "Web shop")
                    @Flow("checkout", label = "Place order")
                    fun place(req: OrderRequest): Order {
                        val order = service.create(req)
                        // @flow checkout>notify: Send receipt
                        mailer.send(order)
                        return order
                    }
                }
                """;
        String service = """
                package shop

                class OrderService(private val orders: OrderStore) {
                    @Flow("checkout>save")
                    fun create(req: OrderRequest): Order = orders.insert(req)
                }

                interface OrderStore { fun insert(req: OrderRequest): Order }

                @EVL.Database("orders-db")
                class JdbcOrderStore : OrderStore {
                    override fun insert(req: OrderRequest): Order { return Order() }
                }
                """;
        FlowModel m = Scanner.scan(List.of(new SourceFile("OrderController.kt", api), new SourceFile("OrderService.kt", service)));
        assertThat(m.problems).isEmpty();
        FlowNode checkout = find(m, "checkout");
        assertThat(checkout.name).isEqualTo("Place order");
        assertThat(checkout.subtitle).isEqualTo("POST /orders");
        assertThat(checkout.caller).isEqualTo("Web shop");
        assertThat(children(checkout)).containsExactly("save", "notify");
        assertThat(find(m, "checkout>notify").name).isEqualTo("Send receipt");
        assertThat(find(m, "checkout>save").uses).containsExactly("database:orders-db");
    }

    @Test
    void reportsBadPaths() {
        FlowModel m = Scanner.scan(List.of(new SourceFile("A.java", "class A { @Flow(\"a>>b\") void f() {} }")));
        assertThat(m.problems).extracting(Problem::level).containsExactly("error");
    }
}
