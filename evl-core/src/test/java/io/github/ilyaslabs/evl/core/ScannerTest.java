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
    void decisionsLoopsAndErrors() {
        String src = """
                import io.github.ilyaslabs.evl.Flow;

                class Checkout {
                    @Flow("checkout")
                    void pay(Order o) {
                        // @flow checkout>resize: Resize [each size in sizes]
                        resize(o);
                        if (o.card()) {
                            // @flow checkout>payment?>card: Charge card [when card]
                            charge(o);
                        } else if (o.wallet()) {
                            // @flow checkout>payment?>wallet [when wallet]
                            debit(o);
                        } else {
                            // @flow checkout>payment?>cod: Cash [when else]
                            cod(o);
                        }
                        notifier.send(o);
                    }
                }

                class Notifier {
                    @Flow(value = "checkout>retry", onError = "IOException")
                    void send(Order o) {}
                }
                """;
        FlowModel m = Scanner.scan(List.of(new SourceFile("Checkout.java", src)));
        assertThat(m.problems).isEmpty();
        assertThat(children(find(m, "checkout"))).containsExactly("resize", "payment?", "retry");
        FlowNode decision = find(m, "checkout>payment?");
        assertThat(decision.kind).isEqualTo("decision");
        assertThat(children(decision)).containsExactly("card", "wallet", "cod");
        assertThat(find(m, "checkout>payment?>card").name).isEqualTo("Charge card");
        assertThat(find(m, "checkout>payment?>card").attrs).containsEntry("when", "card");
        assertThat(find(m, "checkout>payment?>wallet").name).isEqualTo("wallet");
        assertThat(find(m, "checkout>resize").attrs).containsEntry("each", "size in sizes");
        assertThat(find(m, "checkout>retry").attrs).containsEntry("onError", "IOException");
    }

    @Test
    void sharedCodeInTwoFlows() {
        String src = """
                import io.github.ilyaslabs.evl.EVL;
                import io.github.ilyaslabs.evl.Flow;

                class Api {
                    private final Urls urls;
                    @Flow("view")
                    String view() { return urls.build(); }
                    @Flow("asset")
                    String asset() { return urls.build(); }
                    @Flow("thumb")
                    String thumb() { return urls.sign(); }
                }

                class Urls {
                    private final Bucket bucket;
                    String build() {
                        // @flow view>url?>public, asset>url?>public: Public URL [when public]
                        return bucket.publicUrl();
                    }
                    @Flow("other>sign")
                    String sign() { return bucket.presign(); }
                }

                @EVL.Storage("S3")
                class Bucket { String publicUrl() { return ""; } String presign() { return ""; } }
                """;
        FlowModel m = Scanner.scan(List.of(new SourceFile("Api.java", src)));
        assertThat(m.problems).isEmpty();
        for (String flow : List.of("view", "asset")) {
            FlowNode arm = find(m, flow + ">url?>public");
            assertThat(arm.attrs).containsEntry("when", "public");
            assertThat(arm.uses).contains("storage:S3");
        }
        // Code anchored in another flow still counts as this flow's resource use.
        assertThat(find(m, "thumb").uses).contains("storage:S3");
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
        assertThat(checkout.attrs).containsEntry("path", "/orders");
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
