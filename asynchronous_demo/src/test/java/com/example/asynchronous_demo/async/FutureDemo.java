package com.example.asynchronous_demo.async;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Future的缺陷:
 * 1. 在没有阻塞的情况下，无法对Future的结果执行进一步的操作。 Future不会告知你它什么时候完
 * 成，你如果想要得到结果，必须通过一个get()方法，该方法会阻塞直到结果可用为止。它不具备将
 * 回调函数附加到Future后并在Future的结果可用时自动调用回调的能力。<br>
 * 2. 无法解决任务相互依赖的问题。 filterWordFuture和newsFuture的结果不能自动发送给
 * replaceFuture, 需要在replaceFuture中手动获取，所以使用Future不能轻而易举地创建异步工作
 * 流。<br>
 * 3. 不能将多个Future合并在一起。 假设你有多种不同的Future, 你想在它们全部并行完成后然后运行
 * 某个函数，Future很难独立完成这一需要。<br>
 * 4. 没有异常处理。 Future提供的方法中没有专门的API应对异常处理，还需要开发者自己手动异常处
 * 理。<br>
 *
 * @author yueyubo
 * @date 2024-07-03
 */
public class FutureDemo {
    static String path = "D:\\project_code\\java\\myself_demo_all\\asynchronous_demo\\src\\test\\java\\com\\example\\asynchronous_demo\\async\\";

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(5);
        // step 1: 读取敏感词汇 => thread1
        Future<String[]> filterWordFuture = executor.submit(() -> {
            String str = CommonUtils.readFile(path + "filter_words.txt");
            for (String s : str.split(",")) {
                System.out.println("sensitive words = " + s);
            }

            return str.split(",");
        });
        // step 2: 读取新闻稿 => thread2
        Future<String> newsFuture = executor.submit(() -> CommonUtils.readFile(path + "news.txt"));
        // step 3: 替换操作 => thread3
        Future<String> replaceFuture = executor.submit(() -> {
            String[] words = filterWordFuture.get();
            String news = newsFuture.get();
            for (String word : words) {
                if (news.contains(word)) {
                    news = news.replace(word, "**");
                }
            }
            return news;
        });
        // step 4: 打印输出替换后的新闻稿 => main
        String filteredNews = replaceFuture.get();
        System.out.println("filteredNews = " + filteredNews);
        executor.shutdown();

        TimeUnit.SECONDS.sleep(3);
    }

    @Test
    @SneakyThrows
    public void supplyAsyncTest() {
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() ->
                CommonUtils.readFile(path + "news.txt"));

        String str = future.get();//阻塞并等待newsFuture完成
        System.out.println("str = " + str);
    }

    @Test
    @SneakyThrows
    public void thenApplyTest() {
        CompletableFuture<String[]> apply = CompletableFuture.supplyAsync(() -> CommonUtils.readFile(path + "filter_words.txt"))
                .thenApply(sensitiveStr -> {
                    CommonUtils.printTheadLog("sensitiveStr = " + sensitiveStr);
                    return sensitiveStr.split(",");
                });
        String[] sensitiveStr = apply.get();
        System.out.println("Arrays.toString(filterWords) = " +
                Arrays.toString(sensitiveStr));
        CommonUtils.printTheadLog("main end");
    }

    @Test
    @SneakyThrows
    public void convertSensitiveWordsTest() {
        CompletableFuture.supplyAsync(() -> CommonUtils.readFile(path + "filter_words.txt"))
                .thenApply(sensitiveStr -> {
                    CommonUtils.printTheadLog("sensitiveStr = " + sensitiveStr);
                    return sensitiveStr.split(",");
                })
                .thenAccept(sensitives -> {
                    String news = CommonUtils.readFile(path + "news.txt");
                    for (String sensitive : sensitives) {
                        if (news.contains(sensitive)) {
                            news = news.replace(sensitive, "**");
                        }
                    }
                    System.out.println("news = " + news);
                });
    }

    @Test
    @SneakyThrows
    public void thenRunTest() {
        CompletableFuture.supplyAsync(() -> CommonUtils.readFile(path + "filter_words.txt"))
                .thenRun(() -> CommonUtils.printTheadLog("读取文件完毕"));

        CommonUtils.printTheadLog("main continue");
        CommonUtils.sleepSecond(2);
        CommonUtils.printTheadLog("main end");
    }

    @Test
    @SneakyThrows
    public void thenSupplyAsyncTest() {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CompletableFuture<String[]> filterWordFuture = CompletableFuture.supplyAsync(()
                -> {
            CommonUtils.printTheadLog("读取filter_words.txt文件");
            return CommonUtils.readFile(path + "filter_words.txt");
        }, executor).thenApplyAsync(content -> {
            CommonUtils.printTheadLog("把文件内容转换成敏感词数组");
            return content.split(",");
        }, executor);

        CommonUtils.printTheadLog("main continue");
        String[] filterWords = filterWordFuture.get();
        CommonUtils.printTheadLog("filterWords = " +
                Arrays.toString(filterWords));
        executor.shutdown();
        CommonUtils.printTheadLog("main end");
    }

    /**
     * 编排有依赖关系的异步任务<br>
     * 结合上一步异步任务的结果得到下一个新的异步任务中，结果由这个新的异步任务返回
     */
    @Test
    @SneakyThrows
    public void thenComposeTest() {
        readFileFuture(path + "filter_words.txt")
                .thenCompose(FutureDemo::splitFuture)
                .thenAccept(words -> System.out.println("words = " + Arrays.toString(words)));
    }

    /**
     * 编排非依赖关系的异步任务
     */
    @Test
    @SneakyThrows
    public void thenCombineTest() {
        // 需求：替换新闻稿（ news.txt )中敏感词汇，把敏感词汇替换成*，敏感词存储在
        // step 1: 读取filter_words.txt文件内容，并解析成敏感数组
        CompletableFuture<String[]> future1 = CompletableFuture.supplyAsync(() -> {
            CommonUtils.printTheadLog("读取filter_words文件");
            String filterWordsContent =
                    CommonUtils.readFile(path + "filter_words.txt");
            return filterWordsContent.split(",");
        });
        // step 2: 读取news.txt文件内容
        CompletableFuture<String> future2 = CompletableFuture.supplyAsync(() -> {
            CommonUtils.printTheadLog("读取news文件");
            return CommonUtils.readFile(path + "news.txt");
        });
        // step 2: 替换操作
        CompletableFuture<String> combineFuture = future1.thenCombine(future2,
                (filterWords, newsContent) -> {
                    CommonUtils.printTheadLog("替换操作");
                    for (String word : filterWords) {
                        if (newsContent.contains(word)) {
                            newsContent = newsContent.replace(word, "**");
                        }
                    }
                    return newsContent;
                });
        CommonUtils.printTheadLog("main continue");
        String news = combineFuture.get();
        CommonUtils.printTheadLog("news = " + news);
        CommonUtils.printTheadLog("main end");
    }

    /**
     * 合并多个异步任务
     * 需求： 统计news1.txt,news2.txt,news3.txt文件中包含CompletableFuture关键字
     * 的文件的个数
     */
    @Test
    @SneakyThrows
    public void allOfTest() {
        // step 1: 创建List集合存储文件名
        List<String> fileList = Arrays.asList(path + "news.txt", path + "news1.txt", path + "news2.txt");

        // step 2: 根据文件名调用readFileFuture创建多个CompletableFuture,并存入List集合中
        List<CompletableFuture<String>> readFileFutureList =
                fileList.stream().map(FutureDemo::readFileFuture).collect(Collectors.toList());

        Long count = CompletableFuture.allOf(readFileFutureList.toArray(new CompletableFuture[readFileFutureList.size()]))
                .thenApply(result -> readFileFutureList.stream()
                        .map(CompletableFuture::join)
                        .filter(content -> content.contains("CompletableFuture"))
                        .count())
                .join();

        System.out.println("count = " + count);
    }

    @Test
    @SneakyThrows
    public void anyOfTest() {
        CompletableFuture<String> future1 =CompletableFuture.supplyAsync(()->{
            CommonUtils.sleepSecond(2);
            return "Future1的结果";
        });
        CompletableFuture<String> future2 =CompletableFuture.supplyAsync(()->{
            CommonUtils.sleepSecond(1);
            return "Future2的结果";
        });
        CompletableFuture<String> future3 =CompletableFuture.supplyAsync(()->{
            CommonUtils.sleepSecond(3);
            return "Future3的结果";
        });
        CompletableFuture<Object> anyOfFuture = CompletableFuture.anyOf(future1,
                future2, future3);

        Object ret = anyOfFuture.get();
        System.out.println("ret = " + ret);
    }

    @Test
    @SneakyThrows
    public void exceptionallyTest(){
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
// int r = 1 / 0;
            return "result1";
        })
        .thenApply(result -> {
            String str = null;
            int len = str.length();
            return result + " result2";
        })
        .thenApply(result -> result + " result3")
        .exceptionally(ex -> {
            System.out.println("出现异常：" + ex.getMessage());
            return "UnKnown";
        });
    }

    @Test
    @SneakyThrows
    public void handleTest(){
        CommonUtils.printTheadLog("main start");
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
//            int r = 1 / 0;
            return "result1";
        }).handle((result, ex)->{
            CommonUtils.printTheadLog("上一步异常的恢复");
            if(ex != null){
                CommonUtils.printTheadLog("出现异常：" + ex.getMessage());
                return "UnKnown";
            }
            return result;
        });
        CommonUtils.printTheadLog("main continue");
        String ret = future.get();
        CommonUtils.printTheadLog("ret = " + ret);
        CommonUtils.printTheadLog("main end");
    }

    /**
     * 把两个异步任务做比较，异步任务先到结果的，就对先到的结果进行下一步操作。
     */
    @Test
    @SneakyThrows
    public void applyToEitherTest(){
        // 异步任务1
        CompletableFuture<Integer> future1 = CompletableFuture.supplyAsync(() ->
        {
            int x = new Random().nextInt(3);
            CommonUtils.sleepSecond(x);
            CommonUtils.printTheadLog("任务1耗时" + x + "秒");
            return x;
        });
        // 异步任务2
        CompletableFuture<Integer> future2 = CompletableFuture.supplyAsync(() ->
        {
            int y = new Random().nextInt(3);
            CommonUtils.sleepSecond(y);
            CommonUtils.printTheadLog("任务2耗时" + y + "秒");
            return y;
        });

        // 哪些异步任务的结果先到达，就使用哪个异步任务的结果
        CompletableFuture<Integer> future = future1.applyToEither(future2,
                result -> {
                    CommonUtils.printTheadLog("最先到达的结果：" + result);
                    return result;
                });
        CommonUtils.sleepSecond(4);
        Integer ret = future.get();
        CommonUtils.printTheadLog("ret = " + ret);
    }

    /**
     * acceptEither() 把两个异步任务做比较，异步任务先到结果的，就对先到的结果进行下一步操作(消费使用)。
     */
    @Test
    @SneakyThrows
    public void acceptEither(){
        // 异步任务交互 acceptEither
        // 异步任务1
        CompletableFuture<Integer> future1 = CompletableFuture.supplyAsync(() ->
        {
            int x = new Random().nextInt(3);
            CommonUtils.sleepSecond(x);
            CommonUtils.printTheadLog("任务1耗时" + x + "秒");
            return x;
        });
        // 异步任务2
        CompletableFuture<Integer> future2 = CompletableFuture.supplyAsync(() ->
        {
            int y = new Random().nextInt(3);
            CommonUtils.sleepSecond(y);
            CommonUtils.printTheadLog("任务2耗时" + y + "秒");
            return y;
        });
        // 哪个异步任务先完成，就使用异步任务的结果
        future1.acceptEither(future2, result -> {
            CommonUtils.printTheadLog("最先到达的结果：" + result);
        });
        CommonUtils.sleepSecond(4);
    }

    public static CompletableFuture<String> readFileFuture(String fileName) {
        return CompletableFuture.supplyAsync(() -> CommonUtils.readFile(fileName));
    }

    public static CompletableFuture<String[]> splitFuture(String content) {
        return CompletableFuture.supplyAsync(() -> content.split(","));
    }


}
