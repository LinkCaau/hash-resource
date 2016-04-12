### 简介
[前端工程精粹（一）：静态资源版本更新与缓存](http://www.infoq.com/cn/articles/front-end-engineering-and-performance-optimization-part1) maven 插件实现

我们希望得到的是,修改了的文件的hash码是改变了,并且与现在和未修改前的文件的hash码不重复.Hash码并且越短越好.

MD5生成的16进制长度是32位,SHA-256的更长,是64位.最后确定是选CRC32[介绍](https://zh.wikipedia.org/wiki/%E5%BE%AA%E7%92%B0%E5%86%97%E9%A4%98%E6%A0%A1%E9%A9%97) ,不过看到JDK(8) 里面还有个Adler32[介绍](https://en.wikipedia.org/wiki/Adler-32).

理论上来讲，CRC64的碰撞概率大约是每18×10^18个CRC码出现一次。[这里](http://blog.csdn.net/yunhua_lee/article/details/42775039)有人测试过CRC32的碰撞概率,数据显示是1820W数据，冲突数量是38638个，还是比较可观。还有Adler32也可以选择,不过保守点还是选CRC32.

使用要求 :
> * Java 8
> * 只支持绝对路径(正则问题)
> * 不能包含jstl语法(正则问题)

### 使用方法
```xml
<plugins>
    <!-- 先复制要处理的资源,必须放在第一 -->
    <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-resources-plugin</artifactId>
        <version>2.7</version>
        <executions>
            <execution>
                <phase>validate</phase>
                <goals>
                    <goal>copy-resources</goal>
                </goals>
                <configuration>
                    <outputDirectory>${project.build.directory}/prepareWarSource</outputDirectory>
                    <resources>
                        <resource>
                            <directory>${basedir}/webapp</directory>
                        </resource>
                    </resources>
                </configuration>
            </execution>
        </executions>
    </plugin>
    <!--对资源hash和修改引用该资源的文件-->
    <plugin>
        <groupId>com.chaodongyue.maven</groupId>
        <artifactId>hashresource-maven-plugin</artifactId><
        <version>1.0</version>
        <executions>
            <execution>
                <goals>
                    <goal>hash</goal>
                </goals>
                <configuration>
                    <warSourceDirectory>${project.build.directory}/prepareWarSource</warSourceDirectory>
                    <includesHtml>
                        <include>**/*.jsp</include>
                        <include>**/*.html</include>
                        <include>/WEB-INF/tags/*.tag</include>
                    </includesHtml>
                </configuration>
            </execution>
        </executions>
    </plugin>
    <!-- 最后打成war包,必须放最后 -->
    <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-war-plugin</artifactId>
        <configuration>
            <warSourceDirectory>${project.build.directory}/prepareWarSource</warSourceDirectory>
        </configuration>
    </plugin>
</plugins>
```
