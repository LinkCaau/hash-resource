### 简介
[前端工程精粹（一）：静态资源版本更新与缓存](http://www.infoq.com/cn/articles/front-end-engineering-and-performance-optimization-part1) maven 插件实现

我们希望得到的是,修改了的文件的hash码是改变了,并且与现在和未修改前的文件的hash码不重复.Hash码并且越短越好.

MD5生成的16进制长度是32位,SHA-256的更长,是64位.最后确定是选CRC32[介绍](https://zh.wikipedia.org/wiki/%E5%BE%AA%E7%92%B0%E5%86%97%E9%A4%98%E6%A0%A1%E9%A9%97) ,不过看到JDK(8) 里面还有个Adler32[介绍](https://en.wikipedia.org/wiki/Adler-32).

理论上来讲，CRC64的碰撞概率大约是每18×10^18个CRC码出现一次。[这里](http://blog.csdn.net/yunhua_lee/article/details/42775039)有人测试过CRC32的碰撞概率,数据显示是1820W数据，冲突数量是38638个，还是比较可观。还有Adler32也可以选择,不过保守点还是选CRC32.

### 原理
拷贝warSourceDirectory (和maven-war-plugin的默认值一样是${basedir}/src/main/webapp)到 ${project.build.directory}/prepareWarSource 下,然后对js和css进行hash,把hash的值得追加到文件原名字后.

修改所有页面引用资源的地址,把引用的文件名改为hash之后的文件名.war打包的时候将warSourceDirectory改为插件修改文件的目录就可以

### 注意:
> * 建议在服务器上build, 如果开发的时候build, 会导致项目启动时读取不到文件, 因为build之后目标文件会重命名
> * 建议把第三方的包排除了,免得重复处理

### 使用要求 :
> * 支持相对路径,绝对路径

### 使用方法
```xml
<plugins>
    <!--对资源hash和修改引用该资源的文件-->
    <plugin>
        <groupId>com.chaodongyue.maven</groupId>
        <artifactId>hash-resource</artifactId>
        <version>1.0</version>
        <executions>
            <execution>
                <execution>
                    <phase>prepare-package</phase>
                    <goals>
                        <goal>hash</goal>
                    </goals>
                </execution>
                <configuration>
                    <configuration>
                        <excludeResource>
                            <excludeResource>**/*.min.js</excludeResource><!-- 排除已经压缩了的js -->
                            <excludeResource>**/*.min.css</excludeResource><!-- 排除已经压缩了的css -->
                        </excludeResource>
                    </configuration>
            </configuration>
            </execution>
        </executions>
    </plugin>
    <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-war-plugin</artifactId>
        <configuration>
            <warSourceDirectory>${project.build.directory}/prepareWarSource</warSourceDirectory>
        </configuration>
    </plugin>
</plugins>
```